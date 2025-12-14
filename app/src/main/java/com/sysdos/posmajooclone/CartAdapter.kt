package com.sysdos.posmajooclone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

// Model Data untuk Tampilan Keranjang (Perlu ID untuk edit)
data class CartItemDisplay(
    val id: Int,        // ID Produk (PENTING)
    val name: String,
    val price: Double,
    val qty: Int
)

class CartAdapter(
    private var cartItems: List<CartItemDisplay>,
    // Callback Aksi: (ID Produk) -> Unit
    private val onPlus: (Int) -> Unit,
    private val onMinus: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    class CartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvCartItemName)
        val tvPrice: TextView = view.findViewById(R.id.tvCartItemPrice)
        val tvQty: TextView = view.findViewById(R.id.tvCartItemQty)
        val btnPlus: Button = view.findViewById(R.id.btnPlus)
        val btnMinus: Button = view.findViewById(R.id.btnMinus)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart_row, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val item = cartItems[position]

        holder.tvName.text = item.name
        holder.tvQty.text = item.qty.toString()

        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        val totalPerItem = item.price * item.qty
        holder.tvPrice.text = "${format.format(item.price)} x ${item.qty} = ${format.format(totalPerItem)}"

        // Pasang Aksi Tombol
        holder.btnPlus.setOnClickListener { onPlus(item.id) }
        holder.btnMinus.setOnClickListener { onMinus(item.id) }
        holder.btnDelete.setOnClickListener { onDelete(item.id) }
    }

    override fun getItemCount() = cartItems.size

    // Fungsi untuk update data tanpa refresh activity
    fun updateList(newList: List<CartItemDisplay>) {
        cartItems = newList
        notifyDataSetChanged()
    }
}