-- =============================================================================
-- GHOTOK MATRIMONY & OPERATIONS DESK — SUPABASE POSTGRESQL SCHEMA
-- Paste and run this entire script in your Supabase SQL Editor.
-- Firebase is used strictly for Push Notifications (FCM); all database tables
-- and operations run on Supabase PostgreSQL via PostgREST.
-- =============================================================================

-- 1. USERS & PROFILES TABLE (Primary B & G Biodata Moderation Table)
CREATE TABLE IF NOT EXISTS public.users (
    id TEXT PRIMARY KEY,
    uid TEXT NOT NULL,
    profile_code TEXT,
    full_name TEXT DEFAULT '',
    email TEXT DEFAULT '',
    phone_number TEXT DEFAULT '',
    gender TEXT DEFAULT '',
    date_of_birth TEXT DEFAULT '',
    religion TEXT DEFAULT 'Islam',
    marital_status TEXT DEFAULT '',
    present_division TEXT DEFAULT '',
    present_district TEXT DEFAULT '',
    present_upazila TEXT DEFAULT '',
    present_address TEXT DEFAULT '',
    permanent_division TEXT DEFAULT '',
    permanent_district TEXT DEFAULT '',
    permanent_upazila TEXT DEFAULT '',
    permanent_address TEXT DEFAULT '',
    highest_degree TEXT DEFAULT '',
    occupation TEXT DEFAULT '',
    employment_type TEXT DEFAULT '',
    work_location_district TEXT DEFAULT '',
    account_status TEXT DEFAULT 'ACTIVE',
    verification_status TEXT DEFAULT 'UNVERIFIED',
    is_verified BOOLEAN DEFAULT FALSE,
    is_premium BOOLEAN DEFAULT FALSE,
    is_featured BOOLEAN DEFAULT FALSE,
    is_onboarding_completed BOOLEAN DEFAULT FALSE,
    onboarding_completed BOOLEAN DEFAULT FALSE,
    profile_completion_score INTEGER DEFAULT 0,
    photo_url TEXT DEFAULT '',
    fcm_token TEXT DEFAULT '',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_uid ON public.users(uid);
CREATE INDEX IF NOT EXISTS idx_users_profile_code ON public.users(profile_code);
CREATE INDEX IF NOT EXISTS idx_users_gender ON public.users(gender);
CREATE INDEX IF NOT EXISTS idx_users_account_status ON public.users(account_status);

-- Enforce uniqueness on the three identity keys the app relies on for profile-code
-- allocation, auth lookups, and admin grants. Without these, two concurrent signups
-- can both pass the `allocate_unique_profile_code` ownership check and end up with
-- the same profile code / uid / email.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
        WHERE conname = 'uq_users_uid'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users ADD CONSTRAINT uq_users_uid UNIQUE (uid);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
        WHERE conname = 'uq_users_profile_code'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users ADD CONSTRAINT uq_users_profile_code UNIQUE (profile_code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
        WHERE conname = 'uq_users_email'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users ADD CONSTRAINT uq_users_email UNIQUE (email);
    END IF;
END $$;

-- Mirror table `profiles` for Admin Dashboard compatibility
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY,
    uid TEXT NOT NULL,
    profile_code TEXT,
    full_name TEXT DEFAULT '',
    email TEXT DEFAULT '',
    phone_number TEXT DEFAULT '',
    gender TEXT DEFAULT '',
    present_division TEXT DEFAULT '',
    present_district TEXT DEFAULT '',
    account_status TEXT DEFAULT 'ACTIVE',
    verification_status TEXT DEFAULT 'UNVERIFIED',
    is_verified BOOLEAN DEFAULT FALSE,
    is_onboarding_completed BOOLEAN DEFAULT FALSE,
    photo_url TEXT DEFAULT '',
    fcm_token TEXT DEFAULT '',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_premium BOOLEAN DEFAULT FALSE;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_featured BOOLEAN DEFAULT FALSE;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS verification_status TEXT DEFAULT 'UNVERIFIED';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS account_status TEXT DEFAULT 'ACTIVE';

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS verification_status TEXT DEFAULT 'UNVERIFIED';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS account_status TEXT DEFAULT 'ACTIVE';

-- 2. PROFILE CONTACTS TABLE (Protected Guardian & Candidate Contact Details)
CREATE TABLE IF NOT EXISTS public.profile_contacts (
    user_id TEXT PRIMARY KEY,
    candidate_mobile TEXT DEFAULT '',
    guardian_mobile TEXT DEFAULT '',
    whatsapp_number TEXT DEFAULT '',
    contact_email TEXT DEFAULT '',
    present_address TEXT DEFAULT '',
    permanent_address TEXT DEFAULT '',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. PARTNER PREFERENCES TABLE
CREATE TABLE IF NOT EXISTS public.preferences (
    user_id TEXT PRIMARY KEY,
    preferred_age_min INTEGER DEFAULT 18,
    preferred_age_max INTEGER DEFAULT 40,
    preferred_height_min_cm INTEGER DEFAULT 145,
    preferred_height_max_cm INTEGER DEFAULT 195,
    preferred_education TEXT DEFAULT '',
    preferred_occupation TEXT DEFAULT '',
    preferred_location TEXT DEFAULT '',
    preferred_marital_status TEXT DEFAULT '',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. ONBOARDING DRAFTS TABLE
CREATE TABLE IF NOT EXISTS public.onboarding_drafts (
    user_id TEXT PRIMARY KEY,
    current_step INTEGER DEFAULT 1,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. INTEREST PROPOSALS TABLE
CREATE TABLE IF NOT EXISTS public.interests (
    id TEXT PRIMARY KEY,
    sender_id TEXT NOT NULL,
    receiver_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    note TEXT DEFAULT '',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_interests_sender ON public.interests(sender_id);
CREATE INDEX IF NOT EXISTS idx_interests_receiver ON public.interests(receiver_id);

-- 6. CHATS & CHAT MESSAGES TABLES
CREATE TABLE IF NOT EXISTS public.chats (
    id TEXT PRIMARY KEY,
    participant_a TEXT NOT NULL,
    participant_b TEXT NOT NULL,
    interest_id TEXT NOT NULL DEFAULT '',
    last_message TEXT DEFAULT '',
    last_message_sender_id TEXT DEFAULT '',
    last_message_timestamp BIGINT DEFAULT 0,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.chats ADD COLUMN IF NOT EXISTS interest_id TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_chats_participant_a ON public.chats(participant_a);
CREATE INDEX IF NOT EXISTS idx_chats_participant_b ON public.chats(participant_b);

CREATE TABLE IF NOT EXISTS public.chat_messages (
    id TEXT PRIMARY KEY,
    chat_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    receiver_id TEXT DEFAULT '',
    text TEXT NOT NULL DEFAULT '',
    status TEXT DEFAULT 'SENT',
    timestamp_ms BIGINT NOT NULL DEFAULT 0,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_id ON public.chat_messages(chat_id, timestamp_ms);

-- 7. KYC & ID VERIFICATION REQUESTS TABLE
CREATE TABLE IF NOT EXISTS public.verification_requests (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    user_name TEXT DEFAULT '',
    user_gender TEXT DEFAULT '',
    document_type TEXT DEFAULT 'NID',
    document_number TEXT DEFAULT '',
    status TEXT DEFAULT 'PENDING',
    admin_note TEXT DEFAULT '',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    submitted_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_verification_requests_user_id ON public.verification_requests(user_id);

-- 8. BKASH / NAGAD / ROCKET PAYMENT REQUESTS TABLE
CREATE TABLE IF NOT EXISTS public.payment_requests (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    user_name TEXT DEFAULT '',
    plan_id TEXT DEFAULT '',
    plan_name TEXT DEFAULT '',
    amount_bdt INTEGER DEFAULT 0,
    payment_method TEXT DEFAULT 'bKash',
    sender_number TEXT DEFAULT '',
    transaction_id TEXT DEFAULT '',
    status TEXT DEFAULT 'PENDING',
    admin_note TEXT DEFAULT '',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    submitted_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_requests_user_id ON public.payment_requests(user_id);

-- 9. NOTIFICATIONS & BROADCASTS TABLES
CREATE TABLE IF NOT EXISTS public.notifications (
    id TEXT PRIMARY KEY,
    target_user_id TEXT DEFAULT 'ALL',
    title TEXT NOT NULL DEFAULT '',
    message TEXT NOT NULL DEFAULT '',
    type TEXT DEFAULT 'ADMIN_PUSH',
    sender_name TEXT DEFAULT 'Ghotok Admin',
    created_at_ms BIGINT DEFAULT 0,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_target ON public.notifications(target_user_id);

CREATE TABLE IF NOT EXISTS public.broadcasts (
    id TEXT PRIMARY KEY,
    target_user_id TEXT DEFAULT 'ALL',
    title TEXT NOT NULL DEFAULT '',
    message TEXT NOT NULL DEFAULT '',
    type TEXT DEFAULT 'ADMIN_BROADCAST',
    sender_name TEXT DEFAULT 'Ghotok Admin',
    created_at_ms BIGINT DEFAULT 0,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 10. FIREBASE CLOUD MESSAGING (FCM) TOKENS TABLE
-- Stores device push tokens so Supabase Edge Functions or Admin Desk can trigger Firebase Push Notifications
CREATE TABLE IF NOT EXISTS public.fcm_tokens (
    user_id TEXT PRIMARY KEY,
    fcm_token TEXT NOT NULL,
    device_model TEXT DEFAULT '',
    platform TEXT DEFAULT 'android',
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- =============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES FOR APP & ADMIN DESK ACCESS
-- =============================================================================
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profile_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.onboarding_drafts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.interests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chats ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verification_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcasts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fcm_tokens ENABLE ROW LEVEL SECURITY;

-- =============================================================================
-- 10b. ADMIN TABLE & HELPER FUNCTION FOR RLS
-- =============================================================================
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'USER';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_admin BOOLEAN DEFAULT FALSE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'USER';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_admin BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS public.admins (
    email TEXT PRIMARY KEY,
    role TEXT DEFAULT 'SUPER_ADMIN',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.admins ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.is_app_admin()
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.admins a
        WHERE LOWER(a.email) = LOWER(COALESCE(auth.jwt() ->> 'email', ''))
          AND a.is_active = TRUE
    )
    OR COALESCE((auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean, FALSE)
    OR UPPER(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('ADMIN', 'SUPER_ADMIN');
$$;

CREATE OR REPLACE FUNCTION public.protect_interest_participants()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF current_user IN ('postgres', 'supabase_admin', 'service_role')
       OR public.is_app_admin() THEN
        RETURN NEW;
    END IF;

    IF NEW.sender_id IS DISTINCT FROM OLD.sender_id
       OR NEW.receiver_id IS DISTINCT FROM OLD.receiver_id THEN
        RAISE EXCEPTION 'Interest participants cannot be changed';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status
       AND auth.uid()::text IS DISTINCT FROM OLD.receiver_id THEN
        RAISE EXCEPTION 'Only the interest recipient may change its status';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS interests_protect_participants ON public.interests;
CREATE TRIGGER interests_protect_participants
    BEFORE UPDATE ON public.interests
    FOR EACH ROW EXECUTE FUNCTION public.protect_interest_participants();

-- Clean up legacy permissive policies
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'users', 'profiles', 'profile_contacts', 'preferences',
        'onboarding_drafts', 'interests', 'chats', 'chat_messages',
        'verification_requests', 'payment_requests', 'notifications',
        'broadcasts', 'fcm_tokens', 'admins'
    ]
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Allow full access for app and admin" ON public.%I;', tbl);
    END LOOP;
END $$;

-- 1. admins: authenticated users can check if their own email is an active admin; only admins can modify
DROP POLICY IF EXISTS "admins_select_own_or_admin" ON public.admins;
CREATE POLICY "admins_select_own_or_admin" ON public.admins
    FOR SELECT
    USING (
        public.is_app_admin()
        OR LOWER(email) = LOWER(COALESCE(auth.jwt() ->> 'email', ''))
    );

DROP POLICY IF EXISTS "admins_modify_admin_only" ON public.admins;
CREATE POLICY "admins_modify_admin_only" ON public.admins
    FOR ALL
    USING (public.is_app_admin())
    WITH CHECK (public.is_app_admin());

-- 2. users & profiles: authenticated users can read active profiles for matchmaking; only row owner (uid = auth.uid()::text) or admin can insert/update/delete
DROP POLICY IF EXISTS "users_select_authenticated" ON public.users;
DROP POLICY IF EXISTS "users_select_owner_or_admin" ON public.users;
CREATE POLICY "users_select_owner_or_admin" ON public.users
    FOR SELECT
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND uid = auth.uid()::text));

DROP POLICY IF EXISTS "users_insert_owner_or_admin" ON public.users;
CREATE POLICY "users_insert_owner_or_admin" ON public.users
    FOR INSERT
    WITH CHECK (
        public.is_app_admin()
        OR (
            auth.uid() IS NOT NULL
            AND uid = auth.uid()::text
            AND COALESCE(is_admin, FALSE) = FALSE
            AND COALESCE(UPPER(role), 'USER') = 'USER'
        )
    );

DROP POLICY IF EXISTS "users_update_owner_or_admin" ON public.users;
CREATE POLICY "users_update_owner_or_admin" ON public.users
    FOR UPDATE
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND uid = auth.uid()::text))
    WITH CHECK (
        public.is_app_admin()
        OR (
            auth.uid() IS NOT NULL
            AND uid = auth.uid()::text
            AND COALESCE(is_admin, FALSE) = FALSE
            AND COALESCE(UPPER(role), 'USER') = 'USER'
        )
    );

DROP POLICY IF EXISTS "users_delete_owner_or_admin" ON public.users;
CREATE POLICY "users_delete_owner_or_admin" ON public.users
    FOR DELETE
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND uid = auth.uid()::text));

DROP POLICY IF EXISTS "profiles_select_authenticated" ON public.profiles;
DROP POLICY IF EXISTS "profiles_select_owner_or_admin" ON public.profiles;
CREATE POLICY "profiles_select_owner_or_admin" ON public.profiles
    FOR SELECT
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND uid = auth.uid()::text));

DROP POLICY IF EXISTS "profiles_insert_owner_or_admin" ON public.profiles;
CREATE POLICY "profiles_insert_owner_or_admin" ON public.profiles
    FOR INSERT
    WITH CHECK (
        public.is_app_admin()
        OR (
            auth.uid() IS NOT NULL
            AND uid = auth.uid()::text
            AND COALESCE(is_admin, FALSE) = FALSE
            AND COALESCE(UPPER(role), 'USER') = 'USER'
        )
    );

DROP POLICY IF EXISTS "profiles_update_owner_or_admin" ON public.profiles;
CREATE POLICY "profiles_update_owner_or_admin" ON public.profiles
    FOR UPDATE
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND uid = auth.uid()::text))
    WITH CHECK (
        public.is_app_admin()
        OR (
            auth.uid() IS NOT NULL
            AND uid = auth.uid()::text
            AND COALESCE(is_admin, FALSE) = FALSE
            AND COALESCE(UPPER(role), 'USER') = 'USER'
        )
    );

DROP POLICY IF EXISTS "profiles_delete_owner_or_admin" ON public.profiles;
CREATE POLICY "profiles_delete_owner_or_admin" ON public.profiles
    FOR DELETE
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND uid = auth.uid()::text));

-- 3. profile_contacts: owner, admin, or authenticated premium/matched member reading contact details; only owner or admin can write
-- NOTE: contact details (mobile, guardian mobile, whatsapp, email, addresses) are
-- sensitive PII. The previous SELECT policy used `auth.uid() IS NOT NULL`, which let
-- ANY authenticated user read EVERY row. It is now restricted to the row owner,
-- admins, and premium members who have an accepted interest/chat with the owner.
DROP POLICY IF EXISTS "profile_contacts_select_authenticated" ON public.profile_contacts;
CREATE POLICY "profile_contacts_select_owner_or_admin" ON public.profile_contacts
    FOR SELECT
    USING (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text)
        OR (
            auth.uid() IS NOT NULL
            AND EXISTS (
                SELECT 1 FROM public.users requester
                WHERE requester.uid = auth.uid()::text
                  AND requester.is_premium = TRUE
            )
            AND EXISTS (
                SELECT 1 FROM public.interests i
                WHERE (
                    (i.sender_id = public.profile_contacts.user_id AND i.receiver_id = auth.uid()::text)
                    OR (i.receiver_id = public.profile_contacts.user_id AND i.sender_id = auth.uid()::text)
                  )
                  AND UPPER(COALESCE(i.status, '')) = 'ACCEPTED'
            )
        )
    );

DROP POLICY IF EXISTS "profile_contacts_write_owner_or_admin" ON public.profile_contacts;
CREATE POLICY "profile_contacts_write_owner_or_admin" ON public.profile_contacts
    FOR ALL
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text))
    WITH CHECK (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text));

-- 4. preferences, onboarding_drafts, fcm_tokens: strictly isolated to row owner (user_id = auth.uid()::text) or admin
DROP POLICY IF EXISTS "preferences_owner_or_admin" ON public.preferences;
CREATE POLICY "preferences_owner_or_admin" ON public.preferences
    FOR ALL
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text))
    WITH CHECK (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text));

DROP POLICY IF EXISTS "onboarding_drafts_owner_or_admin" ON public.onboarding_drafts;
CREATE POLICY "onboarding_drafts_owner_or_admin" ON public.onboarding_drafts
    FOR ALL
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text))
    WITH CHECK (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text));

DROP POLICY IF EXISTS "fcm_tokens_owner_or_admin" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_owner_or_admin" ON public.fcm_tokens
    FOR ALL
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text))
    WITH CHECK (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text));

-- 5. interests: only sender, receiver, or admin can read/update; only sender or admin can insert
DROP POLICY IF EXISTS "interests_select_participants_or_admin" ON public.interests;
CREATE POLICY "interests_select_participants_or_admin" ON public.interests
    FOR SELECT
    USING (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND (sender_id = auth.uid()::text OR receiver_id = auth.uid()::text))
    );

DROP POLICY IF EXISTS "interests_insert_sender_or_admin" ON public.interests;
CREATE POLICY "interests_insert_sender_or_admin" ON public.interests
    FOR INSERT
    WITH CHECK (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND sender_id = auth.uid()::text)
    );

DROP POLICY IF EXISTS "interests_update_participants_or_admin" ON public.interests;
CREATE POLICY "interests_update_participants_or_admin" ON public.interests
    FOR UPDATE
    USING (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND (sender_id = auth.uid()::text OR receiver_id = auth.uid()::text))
    )
    WITH CHECK (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND (sender_id = auth.uid()::text OR receiver_id = auth.uid()::text))
    );

DROP POLICY IF EXISTS "interests_delete_participants_or_admin" ON public.interests;
CREATE POLICY "interests_delete_participants_or_admin" ON public.interests
    FOR DELETE
    USING (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND (sender_id = auth.uid()::text OR receiver_id = auth.uid()::text))
    );

-- 6. chats & chat_messages: restricted to thread participants or admin
DROP POLICY IF EXISTS "chats_participants_or_admin" ON public.chats;
DROP POLICY IF EXISTS "chats_select_participants_or_admin" ON public.chats;
CREATE POLICY "chats_select_participants_or_admin" ON public.chats
    FOR SELECT
    USING (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND (participant_a = auth.uid()::text OR participant_b = auth.uid()::text))
    );

DROP POLICY IF EXISTS "chats_insert_accepted_interest_or_admin" ON public.chats;
CREATE POLICY "chats_insert_accepted_interest_or_admin" ON public.chats
    FOR INSERT
    WITH CHECK (
        public.is_app_admin()
        OR (
            auth.uid() IS NOT NULL
            AND (participant_a = auth.uid()::text OR participant_b = auth.uid()::text)
            AND participant_a <> participant_b
            AND EXISTS (
                SELECT 1 FROM public.interests i
                WHERE i.id = interest_id
                  AND UPPER(COALESCE(i.status, '')) = 'ACCEPTED'
                  AND (
                    (i.sender_id = participant_a AND i.receiver_id = participant_b)
                    OR (i.sender_id = participant_b AND i.receiver_id = participant_a)
                  )
            )
        )
    );

DROP POLICY IF EXISTS "chats_update_participants_or_admin" ON public.chats;
CREATE POLICY "chats_update_participants_or_admin" ON public.chats
    FOR UPDATE
    USING (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND (participant_a = auth.uid()::text OR participant_b = auth.uid()::text))
    )
    WITH CHECK (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND (participant_a = auth.uid()::text OR participant_b = auth.uid()::text))
    );

DROP POLICY IF EXISTS "chats_delete_participants_or_admin" ON public.chats;
CREATE POLICY "chats_delete_participants_or_admin" ON public.chats
    FOR DELETE
    USING (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND (participant_a = auth.uid()::text OR participant_b = auth.uid()::text))
    );

CREATE OR REPLACE FUNCTION public.protect_chat_participants()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF current_user IN ('postgres', 'supabase_admin', 'service_role')
       OR public.is_app_admin() THEN
        RETURN NEW;
    END IF;

    IF NEW.participant_a IS DISTINCT FROM OLD.participant_a
       OR NEW.participant_b IS DISTINCT FROM OLD.participant_b
       OR NEW.interest_id IS DISTINCT FROM OLD.interest_id THEN
        RAISE EXCEPTION 'Chat participants and accepted interest cannot be changed';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS chats_protect_participants ON public.chats;
CREATE TRIGGER chats_protect_participants
    BEFORE UPDATE ON public.chats
    FOR EACH ROW EXECUTE FUNCTION public.protect_chat_participants();

DROP POLICY IF EXISTS "chat_messages_select_participants_or_admin" ON public.chat_messages;
CREATE POLICY "chat_messages_select_participants_or_admin" ON public.chat_messages
    FOR SELECT
    USING (
        public.is_app_admin()
        OR (
            auth.uid() IS NOT NULL
            AND (
                sender_id = auth.uid()::text
                OR EXISTS (
                    SELECT 1 FROM public.chats c
                    WHERE c.id = chat_messages.chat_id
                      AND (c.participant_a = auth.uid()::text OR c.participant_b = auth.uid()::text)
                )
            )
        )
    );

DROP POLICY IF EXISTS "chat_messages_insert_sender_or_admin" ON public.chat_messages;
CREATE POLICY "chat_messages_insert_sender_or_admin" ON public.chat_messages
    FOR INSERT
    WITH CHECK (
        public.is_app_admin()
        OR (
            auth.uid() IS NOT NULL
            AND sender_id = auth.uid()::text
            AND EXISTS (
                SELECT 1 FROM public.chats c
                WHERE c.id = chat_id
                  AND (c.participant_a = auth.uid()::text OR c.participant_b = auth.uid()::text)
            )
        )
    );

-- 7. verification_requests & payment_requests: users can read/submit their own PENDING requests; only admin can approve
DROP POLICY IF EXISTS "verification_requests_select_owner_or_admin" ON public.verification_requests;
CREATE POLICY "verification_requests_select_owner_or_admin" ON public.verification_requests
    FOR SELECT
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text));

DROP POLICY IF EXISTS "verification_requests_insert_owner_or_admin" ON public.verification_requests;
CREATE POLICY "verification_requests_insert_owner_or_admin" ON public.verification_requests
    FOR INSERT
    WITH CHECK (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text AND UPPER(COALESCE(status, 'PENDING')) = 'PENDING')
    );

DROP POLICY IF EXISTS "verification_requests_update_owner_or_admin" ON public.verification_requests;
CREATE POLICY "verification_requests_update_owner_or_admin" ON public.verification_requests
    FOR UPDATE
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text))
    WITH CHECK (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text AND UPPER(COALESCE(status, 'PENDING')) = 'PENDING')
    );

DROP POLICY IF EXISTS "payment_requests_select_owner_or_admin" ON public.payment_requests;
CREATE POLICY "payment_requests_select_owner_or_admin" ON public.payment_requests
    FOR SELECT
    USING (public.is_app_admin() OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text));

DROP POLICY IF EXISTS "payment_requests_insert_owner_or_admin" ON public.payment_requests;
CREATE POLICY "payment_requests_insert_owner_or_admin" ON public.payment_requests
    FOR INSERT
    WITH CHECK (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text)
    );

DROP POLICY IF EXISTS "payment_requests_update_admin_only" ON public.payment_requests;
CREATE POLICY "payment_requests_update_admin_only" ON public.payment_requests
    FOR UPDATE
    USING (public.is_app_admin())
    WITH CHECK (public.is_app_admin());

-- 8. notifications & broadcasts: authenticated users can read notifications targeted to them or broadcasts; only admin can write
DROP POLICY IF EXISTS "notifications_select_recipient_or_admin" ON public.notifications;
CREATE POLICY "notifications_select_recipient_or_admin" ON public.notifications
    FOR SELECT
    USING (
        public.is_app_admin()
        OR (
            auth.uid() IS NOT NULL
            AND (
                target_user_id IS NULL
                OR target_user_id = ''
                OR target_user_id = 'ALL'
                OR target_user_id = auth.uid()::text
            )
        )
    );

-- 8b. notifications: a user may INSERT their own self-targeted notification (e.g. the
-- PACKAGE_APPROVED row the app writes when an admin activates their plan), but may
-- never read/modify/delete other users' notifications. Admins have full access.
DROP POLICY IF EXISTS "notifications_write_admin_only" ON public.notifications;
CREATE POLICY "notifications_insert_self_or_admin" ON public.notifications
    FOR INSERT
    WITH CHECK (
        public.is_app_admin()
        OR (auth.uid() IS NOT NULL AND target_user_id = auth.uid()::text)
    );

DROP POLICY IF EXISTS "notifications_modify_admin_only" ON public.notifications;
CREATE POLICY "notifications_modify_admin_only" ON public.notifications
    FOR UPDATE
    USING (public.is_app_admin())
    WITH CHECK (public.is_app_admin());

DROP POLICY IF EXISTS "notifications_delete_admin_only" ON public.notifications;
CREATE POLICY "notifications_delete_admin_only" ON public.notifications
    FOR DELETE
    USING (public.is_app_admin());

DROP POLICY IF EXISTS "broadcasts_select_authenticated" ON public.broadcasts;
CREATE POLICY "broadcasts_select_authenticated" ON public.broadcasts
    FOR SELECT
    USING (auth.uid() IS NOT NULL OR public.is_app_admin());

DROP POLICY IF EXISTS "broadcasts_write_admin_only" ON public.broadcasts;
CREATE POLICY "broadcasts_write_admin_only" ON public.broadcasts
    FOR ALL
    USING (public.is_app_admin())
    WITH CHECK (public.is_app_admin());

-- =============================================================================
-- 11. PACKAGE TIER COLUMNS & ADMIN MANUAL UPGRADE / DOWNGRADE HELPER
-- =============================================================================
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS package_tier TEXT DEFAULT 'Free';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS pending_package_tier TEXT DEFAULT '';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS interests_remaining INTEGER DEFAULT 2;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS contacts_remaining INTEGER DEFAULT 0;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS package_activated_at BIGINT DEFAULT 0;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS package_expires_at BIGINT DEFAULT 0;

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS package_tier TEXT DEFAULT 'Free';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS pending_package_tier TEXT DEFAULT '';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS interests_remaining INTEGER DEFAULT 2;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS contacts_remaining INTEGER DEFAULT 0;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS package_activated_at BIGINT DEFAULT 0;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS package_expires_at BIGINT DEFAULT 0;

-- These values affect authorization, moderation, or paid entitlements. RLS
-- checks row ownership, so triggers must also prevent owners from changing
-- protected columns or smuggling equivalent values through the JSON payload.
CREATE OR REPLACE FUNCTION public.strip_client_controlled_profile_fields(p_payload JSONB)
RETURNS JSONB
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT COALESCE(p_payload, '{}'::jsonb) - ARRAY[
        'role', 'isAdmin', 'is_admin',
        'accountStatus', 'account_status',
        'isVerified', 'is_verified', 'verificationStatus', 'verification_status',
        'isPremium', 'is_premium', 'isFeatured', 'is_featured',
        'packageTier', 'package_tier', 'planName', 'plan_name', 'planId', 'plan_id',
        'membershipTier', 'membership_tier', 'subscriptionTier', 'subscription_tier',
        'tier', 'plan', 'pendingPackageTier', 'pending_package_tier',
        'interestsRemaining', 'interests_remaining',
        'contactsRemaining', 'contacts_remaining',
        'interestQuota', 'interestsQuota', 'contactQuota', 'contactsQuota',
        'packageActivatedAt', 'packageExpiresAt', 'package_activated_at',
        'package_expires_at', 'planActivatedAt',
        'planExpiresAt', 'subscriptionExpiresAt', 'packageStartDate',
        'packageEndDate', 'premiumUntil'
    ]::text[];
$$;

CREATE OR REPLACE FUNCTION public.protect_user_privileged_fields()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF current_user IN ('postgres', 'supabase_admin', 'service_role')
       OR public.is_app_admin() THEN
        RETURN NEW;
    END IF;

    NEW.payload := public.strip_client_controlled_profile_fields(NEW.payload);

    IF TG_OP = 'INSERT' THEN
        NEW.role := 'USER';
        NEW.is_admin := FALSE;
        NEW.account_status := 'ACTIVE';
        NEW.verification_status := 'UNSUBMITTED';
        NEW.is_verified := FALSE;
        NEW.package_tier := 'Free';
        NEW.pending_package_tier := '';
        NEW.interests_remaining := 2;
        NEW.contacts_remaining := 0;
        NEW.package_activated_at := 0;
        NEW.package_expires_at := 0;
        NEW.is_premium := FALSE;
        NEW.is_featured := FALSE;
    ELSE
        NEW.role := OLD.role;
        NEW.is_admin := OLD.is_admin;
        NEW.account_status := OLD.account_status;
        NEW.verification_status := OLD.verification_status;
        NEW.is_verified := OLD.is_verified;
        NEW.package_tier := OLD.package_tier;
        NEW.pending_package_tier := OLD.pending_package_tier;
        NEW.interests_remaining := OLD.interests_remaining;
        NEW.contacts_remaining := OLD.contacts_remaining;
        NEW.package_activated_at := OLD.package_activated_at;
        NEW.package_expires_at := OLD.package_expires_at;
        NEW.is_premium := OLD.is_premium;
        NEW.is_featured := OLD.is_featured;
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.protect_profile_privileged_fields()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF current_user IN ('postgres', 'supabase_admin', 'service_role')
       OR public.is_app_admin() THEN
        RETURN NEW;
    END IF;

    NEW.payload := public.strip_client_controlled_profile_fields(NEW.payload);

    IF TG_OP = 'INSERT' THEN
        NEW.role := 'USER';
        NEW.is_admin := FALSE;
        NEW.account_status := 'ACTIVE';
        NEW.verification_status := 'UNSUBMITTED';
        NEW.is_verified := FALSE;
        NEW.package_tier := 'Free';
        NEW.pending_package_tier := '';
        NEW.interests_remaining := 2;
        NEW.contacts_remaining := 0;
        NEW.package_activated_at := 0;
        NEW.package_expires_at := 0;
    ELSE
        NEW.role := OLD.role;
        NEW.is_admin := OLD.is_admin;
        NEW.account_status := OLD.account_status;
        NEW.verification_status := OLD.verification_status;
        NEW.is_verified := OLD.is_verified;
        NEW.package_tier := OLD.package_tier;
        NEW.pending_package_tier := OLD.pending_package_tier;
        NEW.interests_remaining := OLD.interests_remaining;
        NEW.contacts_remaining := OLD.contacts_remaining;
        NEW.package_activated_at := OLD.package_activated_at;
        NEW.package_expires_at := OLD.package_expires_at;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS users_protect_privileged_fields ON public.users;
CREATE TRIGGER users_protect_privileged_fields
    BEFORE INSERT OR UPDATE ON public.users
    FOR EACH ROW EXECUTE FUNCTION public.protect_user_privileged_fields();

DROP TRIGGER IF EXISTS profiles_protect_privileged_fields ON public.profiles;
CREATE TRIGGER profiles_protect_privileged_fields
    BEFORE INSERT OR UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.protect_profile_privileged_fields();

CREATE OR REPLACE FUNCTION public.public_profile_payload(p_payload JSONB)
RETURNS JSONB
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT COALESCE(p_payload, '{}'::jsonb) - ARRAY[
        'dateOfBirth', 'date_of_birth', 'email', 'phone', 'phoneNumber', 'phone_number',
        'contactPhone', 'guardianPhone', 'candidateMobile', 'guardianMobile',
        'contactEmail', 'whatsapp', 'imo', 'facebook', 'instagram',
        'presentAddress', 'permanentAddress', 'present_address', 'permanent_address',
        'fcmToken', 'fcm_token', 'verificationDocType', 'verificationIdNumber',
        'verificationDocUri', 'verificationDocument', 'verificationStatus',
        'isVerified', 'isPremium', 'is_premium', 'isFeatured', 'is_featured',
        'packageTier', 'package_tier', 'planName', 'planId', 'membershipTier',
        'subscriptionTier', 'tier', 'plan', 'pendingPackageTier',
        'interestsRemaining', 'interestsQuota', 'interestQuota',
        'contactsRemaining', 'contactsQuota', 'contactQuota',
        'packageActivatedAt', 'packageExpiresAt', 'planActivatedAt',
        'planExpiresAt', 'subscriptionExpiresAt', 'interestRecords',
        'savedProfileIds', 'privateContacts', 'contactDetails',
        'partnerAgeMin', 'partnerAgeMax', 'partnerHeightMin',
        'partnerHeightMax', 'partnerMaritalStatuses', 'partnerEducation',
        'partnerProfessions', 'partnerLocations', 'partnerFinancialConditions',
        'partnerComplexions', 'partnerIelts', 'partnerOverseasCountries',
        'partnerReligion', 'partnerProfession', 'partnerDistrict',
        'partnerDistricts', 'partnerMarriageTimeline', 'partnerExpectations',
        'role', 'isAdmin', 'is_admin'
    ]::text[];
$$;

-- Matchmaking views expose full biodata while omitting exact DOB, contact
-- channels, street addresses, FCM tokens, and verification documents.
CREATE OR REPLACE VIEW public.matchmaking_users WITH (security_barrier = true) AS
SELECT
    id,
    uid,
    profile_code,
    full_name,
    ''::text AS email,
    ''::text AS phone_number,
    ''::text AS date_of_birth,
    CASE
        WHEN date_of_birth ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, to_date(date_of_birth, 'YYYY-MM-DD')))::integer
        ELSE NULL
    END AS age,
    gender,
    religion,
    marital_status,
    present_division,
    present_district,
    present_upazila,
    ''::text AS present_address,
    permanent_division,
    permanent_district,
    permanent_upazila,
    ''::text AS permanent_address,
    highest_degree,
    occupation,
    employment_type,
    work_location_district,
    'ACTIVE'::text AS account_status,
    CASE WHEN is_verified THEN 'VERIFIED' ELSE 'UNSUBMITTED' END AS verification_status,
    is_verified,
    FALSE AS is_admin,
    'USER'::text AS role,
    is_premium,
    is_featured,
    is_onboarding_completed,
    onboarding_completed,
    profile_completion_score,
    photo_url,
    ''::text AS fcm_token,
    public.public_profile_payload(payload) AS payload,
    package_tier,
    pending_package_tier,
    interests_remaining,
    contacts_remaining,
    package_activated_at,
    package_expires_at,
    created_at,
    updated_at
FROM public.users
WHERE UPPER(COALESCE(account_status, 'ACTIVE')) = 'ACTIVE';

CREATE OR REPLACE VIEW public.matchmaking_profiles WITH (security_barrier = true) AS
SELECT
    id,
    uid,
    profile_code,
    full_name,
    ''::text AS email,
    ''::text AS phone_number,
    ''::text AS date_of_birth,
    CASE
        WHEN COALESCE(payload ->> 'dateOfBirth', '') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, to_date(payload ->> 'dateOfBirth', 'YYYY-MM-DD')))::integer
        ELSE NULL
    END AS age,
    gender,
    COALESCE(NULLIF(payload ->> 'religion', ''), 'Islam') AS religion,
    ''::text AS marital_status,
    present_division,
    present_district,
    ''::text AS present_upazila,
    ''::text AS present_address,
    ''::text AS permanent_division,
    ''::text AS permanent_district,
    ''::text AS permanent_upazila,
    ''::text AS permanent_address,
    ''::text AS highest_degree,
    ''::text AS occupation,
    ''::text AS employment_type,
    ''::text AS work_location_district,
    'ACTIVE'::text AS account_status,
    CASE WHEN is_verified THEN 'VERIFIED' ELSE 'UNSUBMITTED' END AS verification_status,
    is_verified,
    FALSE AS is_admin,
    'USER'::text AS role,
    UPPER(COALESCE(package_tier, 'Free')) <> 'FREE' AS is_premium,
    FALSE AS is_featured,
    is_onboarding_completed,
    is_onboarding_completed AS onboarding_completed,
    0 AS profile_completion_score,
    photo_url,
    ''::text AS fcm_token,
    public.public_profile_payload(payload) AS payload,
    package_tier,
    pending_package_tier,
    interests_remaining,
    contacts_remaining,
    package_activated_at,
    package_expires_at,
    created_at,
    updated_at
FROM public.profiles
WHERE UPPER(COALESCE(account_status, 'ACTIVE')) = 'ACTIVE';

REVOKE ALL ON public.matchmaking_users, public.matchmaking_profiles FROM PUBLIC, anon;
GRANT SELECT ON public.matchmaking_users, public.matchmaking_profiles TO authenticated, service_role;

CREATE TABLE IF NOT EXISTS public.google_play_purchases (
    purchase_token_hash TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    product_id TEXT NOT NULL,
    order_id TEXT NOT NULL DEFAULT '',
    activated_at_ms BIGINT NOT NULL,
    expires_at_ms BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.google_play_purchases ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.google_play_purchases FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.google_play_purchases TO service_role;

CREATE OR REPLACE FUNCTION public.apply_verified_google_play_purchase(
    p_user_id TEXT,
    p_product_id TEXT,
    p_purchase_token_hash TEXT,
    p_order_id TEXT,
    p_activated_at_ms BIGINT,
    p_expires_at_ms BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_tier TEXT;
    v_interests INTEGER;
    v_contacts INTEGER;
    v_has_verified_badge BOOLEAN;
    v_existing public.google_play_purchases%ROWTYPE;
    v_user_id TEXT;
BEGIN
    IF COALESCE(current_setting('request.jwt.claim.role', TRUE), '') <> 'service_role' THEN
        RAISE EXCEPTION 'This operation is restricted to the purchase verification service';
    END IF;

    IF p_purchase_token_hash !~ '^[0-9a-f]{64}$'
       OR p_expires_at_ms <= (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
       OR p_activated_at_ms <= 0 THEN
        RAISE EXCEPTION 'Invalid or expired verified purchase';
    END IF;

    SELECT CASE p_product_id
        WHEN 'ghotok_plan_silver' THEN 'Silver'
        WHEN 'ghotok_plan_gold' THEN 'Gold'
        WHEN 'ghotok_plan_diamond' THEN 'Diamond'
        ELSE NULL
    END INTO v_tier;

    IF v_tier IS NULL THEN
        RAISE EXCEPTION 'Unsupported Google Play product';
    END IF;

    v_interests := CASE v_tier
        WHEN 'Diamond' THEN 999
        WHEN 'Gold' THEN 40
        ELSE 20
    END;
    v_contacts := CASE v_tier
        WHEN 'Diamond' THEN 999
        WHEN 'Gold' THEN 25
        ELSE 10
    END;
    v_has_verified_badge := v_tier IN ('Gold', 'Diamond');

    SELECT uid INTO v_user_id
    FROM public.users
    WHERE uid = p_user_id
    LIMIT 1;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'No profile is registered for the authenticated user';
    END IF;

    SELECT * INTO v_existing
    FROM public.google_play_purchases
    WHERE purchase_token_hash = p_purchase_token_hash
    FOR UPDATE;

    IF FOUND AND v_existing.user_id <> p_user_id THEN
        RAISE EXCEPTION 'This Google Play purchase is already associated with another account';
    END IF;

    IF FOUND AND v_existing.product_id <> p_product_id THEN
        RAISE EXCEPTION 'The Google Play product for this purchase token changed unexpectedly';
    END IF;

    IF FOUND AND v_existing.expires_at_ms >= p_expires_at_ms THEN
        v_tier := CASE v_existing.product_id
            WHEN 'ghotok_plan_gold' THEN 'Gold'
            WHEN 'ghotok_plan_diamond' THEN 'Diamond'
            ELSE 'Silver'
        END;
        v_interests := CASE v_tier
            WHEN 'Diamond' THEN 999
            WHEN 'Gold' THEN 40
            ELSE 20
        END;
        v_contacts := CASE v_tier
            WHEN 'Diamond' THEN 999
            WHEN 'Gold' THEN 25
            ELSE 10
        END;
        v_has_verified_badge := v_tier IN ('Gold', 'Diamond');
        RETURN jsonb_build_object(
            'verified', TRUE,
            'productId', v_existing.product_id,
            'packageTier', v_tier,
            'activatedAtMs', v_existing.activated_at_ms,
            'expiresAtMs', v_existing.expires_at_ms,
            'interestsRemaining', v_interests,
            'contactsRemaining', v_contacts,
            'isVerified', v_has_verified_badge,
            'alreadyApplied', TRUE
        );
    END IF;

    INSERT INTO public.google_play_purchases (
        purchase_token_hash, user_id, product_id, order_id,
        activated_at_ms, expires_at_ms, updated_at
    )
    VALUES (
        p_purchase_token_hash, p_user_id, p_product_id, COALESCE(p_order_id, ''),
        p_activated_at_ms, p_expires_at_ms, NOW()
    )
    ON CONFLICT (purchase_token_hash) DO UPDATE SET
        product_id = EXCLUDED.product_id,
        order_id = EXCLUDED.order_id,
        activated_at_ms = EXCLUDED.activated_at_ms,
        expires_at_ms = EXCLUDED.expires_at_ms,
        updated_at = NOW()
    WHERE public.google_play_purchases.user_id = EXCLUDED.user_id;

    UPDATE public.users
    SET package_tier = v_tier,
        pending_package_tier = '',
        interests_remaining = v_interests,
        contacts_remaining = v_contacts,
        package_activated_at = p_activated_at_ms,
        package_expires_at = p_expires_at_ms,
        is_premium = TRUE,
        is_featured = (v_tier = 'Diamond'),
        is_verified = is_verified OR v_has_verified_badge,
        verification_status = CASE
            WHEN is_verified OR v_has_verified_badge THEN 'VERIFIED'
            ELSE verification_status
        END,
        payload = jsonb_set(
            jsonb_set(
                jsonb_set(
                    jsonb_set(
                        jsonb_set(
                            jsonb_set(COALESCE(payload, '{}'::jsonb),
                                '{packageTier}', to_jsonb(v_tier), TRUE),
                            '{pendingPackageTier}', '""'::jsonb, TRUE),
                        '{interestsRemaining}', to_jsonb(v_interests), TRUE),
                    '{contactsRemaining}', to_jsonb(v_contacts), TRUE),
                '{packageActivatedAt}', to_jsonb(p_activated_at_ms), TRUE),
            '{packageExpiresAt}', to_jsonb(p_expires_at_ms), TRUE),
        updated_at = NOW()
    WHERE uid = p_user_id;

    UPDATE public.profiles
    SET package_tier = v_tier,
        pending_package_tier = '',
        interests_remaining = v_interests,
        contacts_remaining = v_contacts,
        package_activated_at = p_activated_at_ms,
        package_expires_at = p_expires_at_ms,
        is_verified = is_verified OR v_has_verified_badge,
        verification_status = CASE
            WHEN is_verified OR v_has_verified_badge THEN 'VERIFIED'
            ELSE verification_status
        END,
        payload = jsonb_set(
            jsonb_set(
                jsonb_set(
                    jsonb_set(
                        jsonb_set(
                            jsonb_set(COALESCE(payload, '{}'::jsonb),
                                '{packageTier}', to_jsonb(v_tier), TRUE),
                            '{pendingPackageTier}', '""'::jsonb, TRUE),
                        '{interestsRemaining}', to_jsonb(v_interests), TRUE),
                    '{contactsRemaining}', to_jsonb(v_contacts), TRUE),
                '{packageActivatedAt}', to_jsonb(p_activated_at_ms), TRUE),
            '{packageExpiresAt}', to_jsonb(p_expires_at_ms), TRUE),
        updated_at = NOW()
    WHERE uid = p_user_id;

    RETURN jsonb_build_object(
        'verified', TRUE,
        'productId', p_product_id,
        'packageTier', v_tier,
        'activatedAtMs', p_activated_at_ms,
        'expiresAtMs', p_expires_at_ms,
        'interestsRemaining', v_interests,
        'contactsRemaining', v_contacts,
        'isVerified', v_has_verified_badge,
        'alreadyApplied', FALSE
    );
END;
$$;

REVOKE ALL ON FUNCTION public.apply_verified_google_play_purchase(
    TEXT, TEXT, TEXT, TEXT, BIGINT, BIGINT
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.apply_verified_google_play_purchase(
    TEXT, TEXT, TEXT, TEXT, BIGINT, BIGINT
) TO service_role;

-- Helper function for Web Admin Panel or SQL Editor to upgrade/downgrade any user:
-- Example usage: SELECT public.admin_change_user_package('G600001', 'Gold');
CREATE OR REPLACE FUNCTION public.admin_change_user_package(
    p_target_id_or_code TEXT,
    p_new_tier TEXT,
    p_interests INTEGER DEFAULT NULL,
    p_contacts INTEGER DEFAULT NULL
)
RETURNS VOID AS $$
DECLARE
    v_tier TEXT;
    v_interests INTEGER;
    v_contacts INTEGER;
    v_is_premium BOOLEAN;
BEGIN
    v_tier := CASE
        WHEN LOWER(p_new_tier) LIKE '%diamond%' THEN 'Diamond'
        WHEN LOWER(p_new_tier) LIKE '%gold%' THEN 'Gold'
        WHEN LOWER(p_new_tier) LIKE '%silver%' THEN 'Silver'
        ELSE 'Free'
    END;

    v_interests := COALESCE(p_interests, CASE
        WHEN v_tier = 'Diamond' THEN 999
        WHEN v_tier = 'Gold' THEN 60
        WHEN v_tier = 'Silver' THEN 25
        ELSE 2
    END);

    v_contacts := COALESCE(p_contacts, CASE
        WHEN v_tier = 'Diamond' THEN 999
        WHEN v_tier = 'Gold' THEN 30
        WHEN v_tier = 'Silver' THEN 10
        ELSE 0
    END);

    v_is_premium := (v_tier <> 'Free');

    UPDATE public.users
    SET package_tier = v_tier,
        pending_package_tier = '',
        interests_remaining = v_interests,
        contacts_remaining = v_contacts,
        package_activated_at = CASE
            WHEN v_tier = 'Free' THEN 0
            ELSE (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
        END,
        package_expires_at = 0,
        is_premium = v_is_premium,
        payload = jsonb_set(
            jsonb_set(
                jsonb_set(
                    jsonb_set(
                        COALESCE(payload, '{}'::jsonb),
                        '{packageTier}', to_jsonb(v_tier), true
                    ),
                    '{pendingPackageTier}', '""'::jsonb, true
                ),
                '{interestsRemaining}', to_jsonb(v_interests), true
            ),
            '{contactsRemaining}', to_jsonb(v_contacts), true
        ),
        updated_at = NOW()
    WHERE id = p_target_id_or_code
       OR uid = p_target_id_or_code
       OR UPPER(profile_code) = UPPER(p_target_id_or_code);

    UPDATE public.profiles
    SET package_tier = v_tier,
        pending_package_tier = '',
        interests_remaining = v_interests,
        contacts_remaining = v_contacts,
        package_activated_at = CASE
            WHEN v_tier = 'Free' THEN 0
            ELSE (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
        END,
        package_expires_at = 0,
        payload = jsonb_set(
            jsonb_set(
                jsonb_set(
                    jsonb_set(
                        COALESCE(payload, '{}'::jsonb),
                        '{packageTier}', to_jsonb(v_tier), true
                    ),
                    '{pendingPackageTier}', '""'::jsonb, true
                ),
                '{interestsRemaining}', to_jsonb(v_interests), true
            ),
            '{contactsRemaining}', to_jsonb(v_contacts), true
        ),
        updated_at = NOW()
    WHERE id = p_target_id_or_code
       OR uid = p_target_id_or_code
       OR UPPER(profile_code) = UPPER(p_target_id_or_code);
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 12. PARAMETERIZED ADMIN GRANT FUNCTION (NO HARDCODED EMAILS IN SCHEMA)
-- =============================================================================
-- Example usage in Supabase SQL Editor:
--   SELECT public.grant_admin_by_email('admin@yourdomain.com');
CREATE OR REPLACE FUNCTION public.grant_admin_by_email(
    p_email TEXT
)
RETURNS VOID AS $$
DECLARE
    v_clean_email TEXT;
BEGIN
    v_clean_email := LOWER(TRIM(p_email));
    IF v_clean_email IS NULL OR v_clean_email = '' THEN
        RAISE EXCEPTION 'Admin email must not be empty';
    END IF;

    INSERT INTO public.admins (email, role, is_active)
    VALUES (v_clean_email, 'SUPER_ADMIN', TRUE)
    ON CONFLICT (email) DO UPDATE SET role = 'SUPER_ADMIN', is_active = TRUE;

    UPDATE public.users
    SET role = 'ADMIN',
        is_admin = TRUE,
        payload = jsonb_set(
            jsonb_set(COALESCE(payload, '{}'::jsonb), '{role}', '"ADMIN"'::jsonb, true),
            '{isAdmin}', 'true'::jsonb, true
        )
    WHERE LOWER(email) = v_clean_email;

    UPDATE public.profiles
    SET role = 'ADMIN',
        is_admin = TRUE,
        payload = jsonb_set(
            jsonb_set(COALESCE(payload, '{}'::jsonb), '{role}', '"ADMIN"'::jsonb, true),
            '{isAdmin}', 'true'::jsonb, true
        )
    WHERE LOWER(email) = v_clean_email;

    UPDATE auth.users
    SET raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"role": "ADMIN", "is_admin": true}'::jsonb,
        raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || '{"role": "ADMIN", "is_admin": true}'::jsonb
    WHERE LOWER(email) = v_clean_email;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 13. ATOMIC PROFILE CODE COUNTER TABLE & O(1) ALLOCATION RPC
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.profile_code_counters (
    gender_prefix TEXT PRIMARY KEY,
    last_serial INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.profile_code_counters ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "profile_code_counters_select_authenticated" ON public.profile_code_counters;
CREATE POLICY "profile_code_counters_select_authenticated" ON public.profile_code_counters
    FOR SELECT
    USING (auth.uid() IS NOT NULL OR public.is_app_admin());

CREATE OR REPLACE FUNCTION public.allocate_unique_profile_code(
    p_uid TEXT,
    p_gender TEXT DEFAULT 'Male',
    p_preferred_code TEXT DEFAULT '',
    p_year_digit TEXT DEFAULT '6'
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_prefix TEXT;
    v_year TEXT;
    v_existing_code TEXT;
    v_max_existing INTEGER;
    v_next_serial INTEGER;
    v_candidate TEXT;
BEGIN
    v_prefix := CASE
        WHEN LOWER(COALESCE(p_gender, '')) IN ('female', 'bride', 'woman', 'girl', 'f') THEN 'B'
        ELSE 'G'
    END;
    v_year := COALESCE(NULLIF(SUBSTRING(TRIM(COALESCE(p_year_digit, '')) FROM 1 FOR 1), ''), '6');

    -- 1. Return user's own existing valid profile code if already assigned
    IF COALESCE(TRIM(p_uid), '') <> '' THEN
        SELECT COALESCE(NULLIF(profile_code, ''), id)
        INTO v_existing_code
        FROM public.users
        WHERE uid = TRIM(p_uid)
          AND UPPER(COALESCE(NULLIF(profile_code, ''), id)) ~ ('^' || v_prefix || '[0-9]{6}$')
        ORDER BY updated_at DESC NULLS LAST
        LIMIT 1;

        IF v_existing_code IS NOT NULL AND v_existing_code <> '' THEN
            RETURN UPPER(v_existing_code);
        END IF;
    END IF;

    -- 2. Honor preferred code if valid for gender and unowned or owned by p_uid
    IF UPPER(TRIM(COALESCE(p_preferred_code, ''))) ~ ('^' || v_prefix || '[0-9]{6}$') THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.users
            WHERE (UPPER(id) = UPPER(TRIM(p_preferred_code)) OR UPPER(profile_code) = UPPER(TRIM(p_preferred_code)))
              AND COALESCE(uid, '') <> COALESCE(TRIM(p_uid), '')
        ) THEN
            RETURN UPPER(TRIM(p_preferred_code));
        END IF;
    END IF;

    -- 3. Seed counter from current max serial for this gender prefix if needed
    SELECT COALESCE(MAX(NULLIF(SUBSTRING(UPPER(COALESCE(NULLIF(profile_code, ''), id)) FROM 3 FOR 5), '')::INTEGER), 0)
    INTO v_max_existing
    FROM public.users
    WHERE UPPER(COALESCE(NULLIF(profile_code, ''), id)) ~ ('^' || v_prefix || '[0-9]{6}$');

    -- 4. Atomic INSERT ... ON CONFLICT counter increment
    INSERT INTO public.profile_code_counters (gender_prefix, last_serial, updated_at)
    VALUES (v_prefix, v_max_existing + 1, NOW())
    ON CONFLICT (gender_prefix) DO UPDATE
    SET last_serial = GREATEST(public.profile_code_counters.last_serial, v_max_existing) + 1,
        updated_at = NOW()
    RETURNING last_serial INTO v_next_serial;

    v_candidate := v_prefix || v_year || LPAD(v_next_serial::TEXT, 5, '0');
    RETURN v_candidate;
END;
$$;

-- =============================================================================
-- 14. SUPABASE REALTIME PUBLICATION FOR CHATS, INTERESTS & NOTIFICATIONS
-- =============================================================================
DO $$
DECLARE
    rt_tbl TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        FOREACH rt_tbl IN ARRAY ARRAY[
            'chats', 'chat_messages', 'interests', 'notifications', 'broadcasts', 'users'
        ]
        LOOP
            IF NOT EXISTS (
                SELECT 1
                FROM pg_publication_tables
                WHERE pubname = 'supabase_realtime'
                  AND schemaname = 'public'
                  AND tablename = rt_tbl
            ) THEN
                EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I;', rt_tbl);
            END IF;
        END LOOP;
    END IF;
END $$;
