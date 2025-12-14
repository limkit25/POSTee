package com.sysdos.posmajooclone // SESUAIKAN PACKAGE

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private var productList: List<Product>,
    private val onClick: (Product) -> Unit,
    private val onLongClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        val tvStock: TextView = itemView.findViewById(R.id.tvProductStock) // Sekarang aman karena XML sudah ada
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]
        holder.tvName.text = product.name
        holder.tvStock.text = "Stok: ${product.stock}"

        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        holder.tvPrice.text = format.format(product.price)

        holder.itemView.setOnClickListener { onClick(product) }
        holder.itemView.setOnLongClickListener {
            onLongClick(product)
            true
        }
    }

    override fun getItemCount(): Int = productList.size

    fun updateData(newList: List<Product>) {
        productList = newList
        notifyDataSetChanged()
    }
}