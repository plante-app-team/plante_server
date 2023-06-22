package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location

@Location("/product_data_no_auth/")
data class ProductDataParamsNoAuth(val barcode: String)

fun productDataNoAuth(params: ProductDataParamsNoAuth) = productData(ProductDataParams(params.barcode), null)
