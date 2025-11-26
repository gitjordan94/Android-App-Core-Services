package app.core.services.billing

class BillingClientException(
    val error: BillingError,
    override val message: String? = error.description,
) : Exception() {
    constructor(error: BillingError) : this(error, error.description)
}

enum class BillingError(val description: String) {
    NetworkError("Error performing request."),
    ProductNotAvailableForPurchaseError("Product is not available for purchase."),
    DeveloperError("Developer error."),
    UnknownError("Unknown error."),
    ProductAlreadyPurchasedError("This product is already active for the user."),
    PurchaseCancelledError("Purchase was cancelled."),
}