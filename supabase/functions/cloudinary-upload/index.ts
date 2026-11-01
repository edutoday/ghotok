// Supabase Edge Function: cloudinary-upload
// Configure CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET,
// and CLOUDINARY_UPLOAD_PRESET before deployment.
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const MAX_FILE_BYTES = 1_048_576;
const MAX_REQUEST_BYTES = 1_500_000;
const IMAGE_EXTENSIONS: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

async function sha1Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const hashBuffer = await crypto.subtle.digest("SHA-1", data);
  return Array.from(new Uint8Array(hashBuffer), (byte) =>
    byte.toString(16).padStart(2, "0")
  ).join("");
}

async function readBoundedBody(request: Request): Promise<Uint8Array> {
  const reader = request.body?.getReader();
  if (!reader) return new Uint8Array();

  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      totalBytes += value.byteLength;
      if (totalBytes > MAX_REQUEST_BYTES) {
        await reader.cancel();
        throw new Error("UPLOAD_TOO_LARGE");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }

  const body = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return body;
}

async function hasExpectedImageSignature(file: File): Promise<boolean> {
  const bytes = new Uint8Array(await file.slice(0, 12).arrayBuffer());
  switch (file.type.toLowerCase()) {
    case "image/jpeg":
      return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
    case "image/png":
      return bytes.length >= 8 &&
        bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47 &&
        bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a;
    case "image/webp":
      return bytes.length >= 12 &&
        String.fromCharCode(...bytes.slice(0, 4)) === "RIFF" &&
        String.fromCharCode(...bytes.slice(8, 12)) === "WEBP";
    default:
      return false;
  }
}

async function authenticateUser(request: Request): Promise<boolean> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim().replace(/\/+$/, "") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")?.trim() ?? "";
  const authorization = request.headers.get("authorization") ?? "";
  const jwt = authorization.match(/^Bearer\s+(.+)$/i)?.[1] ?? "";
  if (!supabaseUrl || !anonKey || !jwt) return false;

  const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { apikey: anonKey, Authorization: `Bearer ${jwt}` },
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) return false;
  const user = await response.json();
  return typeof user.id === "string" && user.id.length > 0;
}

serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") {
    return jsonResponse({ error: { message: "Method not allowed. Use POST." } }, 405);
  }

  const contentLength = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(contentLength) && contentLength > MAX_REQUEST_BYTES) {
    return jsonResponse({ error: { message: "Image upload is too large." } }, 413);
  }

  try {
    const cloudName = Deno.env.get("CLOUDINARY_CLOUD_NAME")?.trim() ?? "";
    const apiKey = Deno.env.get("CLOUDINARY_API_KEY")?.trim() ?? "";
    const apiSecret = Deno.env.get("CLOUDINARY_API_SECRET")?.trim() ?? "";
    const uploadPreset = Deno.env.get("CLOUDINARY_UPLOAD_PRESET")?.trim() ?? "";
    if (!cloudName || !apiKey || !apiSecret || !uploadPreset) {
      console.error("Cloudinary upload credentials or signed preset are not configured.");
      return jsonResponse({ error: { message: "Upload service is not configured." } }, 503);
    }
    if (!/^[A-Za-z0-9_-]+$/.test(cloudName)) {
      console.error("Cloudinary cloud name has an invalid format.");
      return jsonResponse({ error: { message: "Upload service is not configured." } }, 503);
    }

    if (!(await authenticateUser(request))) {
      return jsonResponse({ error: { message: "A valid signed-in account is required." } }, 401);
    }

    let requestBody: Uint8Array;
    try {
      requestBody = await readBoundedBody(request);
    } catch (error) {
      if (error instanceof Error && error.message === "UPLOAD_TOO_LARGE") {
        return jsonResponse({ error: { message: "Image upload is too large." } }, 413);
      }
      throw error;
    }

    const contentType = request.headers.get("content-type")?.toLowerCase() ?? "";
    let uploadedFile: File | null = null;
    if (contentType.includes("multipart/form-data")) {
      try {
        const formRequest = new Request(request.url, {
          method: "POST",
          headers: request.headers,
          body: requestBody,
        });
        const incomingForm = await formRequest.formData();
        const file = incomingForm.get("file");
        if (file instanceof File) uploadedFile = file;
      } catch {
        return jsonResponse({ error: { message: "Invalid multipart upload." } }, 400);
      }
    } else if (contentType.includes("application/json")) {
      let body: Record<string, unknown>;
      try {
        body = JSON.parse(new TextDecoder().decode(requestBody));
      } catch {
        return jsonResponse({ error: { message: "Invalid JSON upload." } }, 400);
      }

      const encodedFile = typeof body.fileBase64 === "string" ? body.fileBase64.trim() : "";
      const dataUrlMatch = encodedFile.match(
        /^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/]+={0,2})$/i,
      );
      const rawBase64Match = encodedFile.match(/^([A-Za-z0-9+/]+={0,2})$/);
      const mimeType = dataUrlMatch?.[1].toLowerCase() ?? "image/jpeg";
      const base64 = dataUrlMatch?.[2] ?? rawBase64Match?.[1] ?? "";
      if (!base64) {
        return jsonResponse({ error: { message: "A valid image file is required." } }, 400);
      }
      try {
        const decoded = Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
        uploadedFile = new File([decoded], `upload.${IMAGE_EXTENSIONS[mimeType]}`, { type: mimeType });
      } catch {
        return jsonResponse({ error: { message: "Invalid base64 image data." } }, 400);
      }
    } else {
      return jsonResponse({ error: { message: "Use multipart/form-data or application/json." } }, 415);
    }

    if (!uploadedFile) {
      return jsonResponse({ error: { message: "Missing image file in request payload." } }, 400);
    }
    if (uploadedFile.size === 0) {
      return jsonResponse({ error: { message: "Image file is empty." } }, 400);
    }
    if (uploadedFile.size > MAX_FILE_BYTES) {
      return jsonResponse({ error: { message: "Image exceeds the 1 MB upload limit." } }, 413);
    }
    if (!IMAGE_EXTENSIONS[uploadedFile.type.toLowerCase()] ||
        !(await hasExpectedImageSignature(uploadedFile))) {
      return jsonResponse({ error: { message: "Only valid JPEG, PNG, or WebP images are accepted." } }, 415);
    }

    const cloudinaryForm = new FormData();
    cloudinaryForm.append("file", uploadedFile, `upload.${IMAGE_EXTENSIONS[uploadedFile.type.toLowerCase()]}`);
    const timestamp = Math.floor(Date.now() / 1000).toString();
    const paramsToSign = `timestamp=${timestamp}&upload_preset=${uploadPreset}`;
    const signature = await sha1Hex(`${paramsToSign}${apiSecret}`);
    cloudinaryForm.append("api_key", apiKey);
    cloudinaryForm.append("timestamp", timestamp);
    cloudinaryForm.append("upload_preset", uploadPreset);
    cloudinaryForm.append("signature", signature);

    const cloudinaryResponse = await fetch(
      `https://api.cloudinary.com/v1_1/${cloudName}/image/upload`,
      {
        method: "POST",
        body: cloudinaryForm,
        signal: AbortSignal.timeout(30_000),
      },
    );
    if (!cloudinaryResponse.ok) {
      console.error("Cloudinary rejected image upload with status:", cloudinaryResponse.status);
      return jsonResponse({ error: { message: "Image upload failed. Please retry." } }, 502);
    }

    const responseJson = await cloudinaryResponse.json();
    const secureUrl = typeof responseJson.secure_url === "string" ? responseJson.secure_url : "";
    if (!secureUrl.startsWith(`https://res.cloudinary.com/${cloudName}/image/upload/`)) {
      console.error("Cloudinary returned an invalid secure image URL.");
      return jsonResponse({ error: { message: "Image upload failed. Please retry." } }, 502);
    }
    return jsonResponse({
      secure_url: secureUrl,
      public_id: typeof responseJson.public_id === "string" ? responseJson.public_id : "",
    }, 200);
  } catch (error) {
    console.error(
      "Cloudinary image upload failed:",
      error instanceof Error ? error.message : "Unexpected error",
    );
    return jsonResponse({ error: { message: "Image upload failed. Please retry." } }, 500);
  }
});
