// BillingManager.kt — compra única via Google Play Billing (épico 8.7 / regra 11.4). Produto
// NÃO-CONSUMÍVEL `iseelife_desbloqueio_completo`: desbloqueia o disparo para sempre. A posse é
// verificada localmente (queryPurchasesAsync) — offline após a 1ª confirmação. O preço vem da
// Play (formattedPrice), nunca hardcoded. Equivalente ao StoreManager (StoreKit) do iOS.
//
// Observação: BillingClient exige Google Play Services (não funciona em emulador AOSP). Em debug,
// há `simularCompra()` para validar o fluxo do gate sem a Play; a compra real testa-se no aparelho.

package com.dablab.iseelife.compras

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
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

class BillingManager(context: Context) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        const val PRODUTO = "iseelife_desbloqueio_completo"
    }

    private val main = Handler(Looper.getMainLooper())
    private var detalhes: ProductDetails? = null

    // Estado observável pela UI (paywall / gate).
    var precoFormatado by mutableStateOf<String?>(null)
        private set
    var possui by mutableStateOf(false)
        private set

    // Chamado quando a posse passa a true (compra confirmada ou restaurada) — usado para salvar a
    // foto pendente e fechar o paywall sem novo toque (regra 11.4).
    var aoDesbloquear: (() -> Unit)? = null

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init { conectar() }

    private fun conectar() {
        client.startConnection(this)
    }

    override fun onBillingSetupFinished(resultado: BillingResult) {
        if (resultado.responseCode == BillingClient.BillingResponseCode.OK) {
            consultarProduto()
            consultarCompras()
        }
    }

    override fun onBillingServiceDisconnected() {
        // Reconexão preguiçosa: tenta de novo na próxima ação (comprar/restaurar).
    }

    // Carrega os detalhes do produto (preço formatado).
    private fun consultarProduto() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUTO)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        // Billing 8: o callback devolve QueryProductDetailsResult (não mais List<ProductDetails>).
        client.queryProductDetailsAsync(params) { _, resultado ->
            val pd = resultado.productDetailsList.firstOrNull()
            main.post {
                detalhes = pd
                // Billing 8 modela one-time products com purchase options (lista de ofertas).
                precoFormatado = pd?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                    ?: pd?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    // Inicia o fluxo de compra (regra 11.4). Precisa da Activity em primeiro plano.
    fun comprar(activity: Activity) {
        val pd = detalhes ?: run { conectar(); return }
        // Billing 8: informa a purchase option (offer token) da oferta única, quando existir.
        val offerToken = pd.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        val produtoParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .apply { if (offerToken != null) setOfferToken(offerToken) }
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(produtoParams))
            .build()
        client.launchBillingFlow(activity, params)
    }

    // "Restaurar compra" — reconsulta as compras (posse offline após 1ª confirmação).
    fun restaurar() {
        if (!client.isReady) { conectar(); return }
        consultarCompras()
    }

    private fun consultarCompras() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { _, compras -> tratarCompras(compras) }
    }

    override fun onPurchasesUpdated(resultado: BillingResult, compras: MutableList<Purchase>?) {
        if (resultado.responseCode == BillingClient.BillingResponseCode.OK && compras != null) {
            tratarCompras(compras)
        }
    }

    // Confirma (acknowledge) as compras válidas do produto e marca a posse.
    private fun tratarCompras(compras: List<Purchase>) {
        for (p in compras) {
            if (p.products.contains(PRODUTO) && p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!p.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                    client.acknowledgePurchase(ack) {}
                }
                marcarPosse()
            }
        }
    }

    private fun marcarPosse() {
        main.post {
            if (!possui) {
                possui = true
                aoDesbloquear?.invoke()
            }
        }
    }

    // Só em debug: simula a compra para validar o fluxo do gate sem a Play (emulador AOSP).
    fun simularCompra() {
        marcarPosse()
    }
}
