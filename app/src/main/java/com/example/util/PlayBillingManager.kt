package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.data.model.MembershipPackage
import com.example.data.model.MembershipPackages
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PlayBillingManager"

class PlayBillingManager(
    context: Context,
    private val onPurchaseCompleted: (MembershipPackage, String, String) -> Unit,
    private val onBillingError: (String) -> Unit
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val productDetailsByIdMutable =
        MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    private val isPlayStoreConnectedMutable = MutableStateFlow(false)

    val productDetailsById: StateFlow<Map<String, ProductDetails>> =
        productDetailsByIdMutable.asStateFlow()
    val isPlayStoreConnected: StateFlow<Boolean> =
        isPlayStoreConnectedMutable.asStateFlow()

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startBillingConnection() {
        if (billingClient.isReady) {
            queryProductDetails()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    reportError("Google Play Billing setup failed: ${result.debugMessage}")
                    return
                }
                isPlayStoreConnectedMutable.value = true
                queryProductDetails()
            }

            override fun onBillingServiceDisconnected() {
                isPlayStoreConnectedMutable.value = false
                Log.w(TAG, "Google Play Billing service disconnected.")
            }
        })
    }

    fun endConnection() {
        isPlayStoreConnectedMutable.value = false
        if (billingClient.isReady) billingClient.endConnection()
    }

    fun getFormattedPlayPrice(pkg: MembershipPackage): String? {
        val details = productDetailsByIdMutable.value[pkg.productId] ?: return null
        return details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.formattedPrice
    }

    fun launchPackagePurchase(activity: Activity?, pkg: MembershipPackage) {
        if (activity == null) {
            reportError("Unable to start Google Play purchase without an active screen.")
            return
        }
        if (!billingClient.isReady) {
            reportError("Google Play Billing is not connected. Please try again.")
            return
        }
        val details = productDetailsByIdMutable.value[pkg.productId]
        if (details == null) {
            reportError("This package is not currently available in Google Play.")
            return
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            reportError("No Google Play subscription offer is available for this package.")
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            reportError("Could not start Google Play purchase: ${result.debugMessage}")
        }
    }

    fun restorePurchases(onRestored: (MembershipPackage?) -> Unit) {
        if (!billingClient.isReady) {
            reportError("Google Play Billing is not connected. Please try again.")
            onRestored(null)
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                reportError("Could not restore Google Play purchases: ${result.debugMessage}")
                onRestored(null)
                return@queryPurchasesAsync
            }
            val purchase = purchases.firstOrNull {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            val pkg = purchase?.products
                ?.firstNotNullOfOrNull(MembershipPackages::byProductId)
            if (purchase != null && pkg != null) {
                processPurchasedPurchase(purchase, pkg)
                onRestored(pkg)
            } else {
                onRestored(null)
            }
        }
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach { purchase ->
                    val pkg = purchase.products
                        .firstNotNullOfOrNull(MembershipPackages::byProductId)
                    if (pkg == null) {
                        Log.w(TAG, "Ignoring purchase with an unknown product ID.")
                    } else if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        processPurchasedPurchase(purchase, pkg)
                    } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                        reportError("Your Google Play purchase is pending.")
                    }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> reportError("Google Play purchase failed: ${result.debugMessage}")
        }
    }

    private fun processPurchasedPurchase(
        purchase: Purchase,
        pkg: MembershipPackage
    ) {
        completePurchase(purchase, pkg)
    }

    private fun completePurchase(purchase: Purchase, pkg: MembershipPackage) {
        FirebaseAnalytics.getInstance(appContext).logEvent(
            FirebaseAnalytics.Event.PURCHASE,
            android.os.Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_ID, pkg.productId)
                putString(FirebaseAnalytics.Param.ITEM_NAME, pkg.nameEn)
                putString(FirebaseAnalytics.Param.CURRENCY, "BDT")
                putDouble(FirebaseAnalytics.Param.VALUE, pkg.priceBdt.toDouble())
            }
        )
        onPurchaseCompleted(pkg, purchase.orderId.orEmpty(), purchase.purchaseToken)
    }

    private fun queryProductDetails() {
        val products = MembershipPackages.ALL
            .filter { it.priceBdt > 0 }
            .map { pkg ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(pkg.productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        billingClient.queryProductDetailsAsync(params) { result, productDetails ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                reportError("Could not load Google Play packages: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            productDetailsByIdMutable.value =
                productDetails.associateBy { it.productId }
        }
    }

    private fun reportError(message: String) {
        Log.e(TAG, message)
        onBillingError(message)
    }
}
