package com.sysdos.posmajooclone // ADJUST TO YOUR PACKAGE NAME

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

data class CartItemDisplay(val name: String, val price: Double, val qty: Int)

class CartAdapter(private val cartItems: List<CartItemDisplay>) : RecyclerView.Adapter<CartAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvCartItemName)
        val tvPrice: TextView = v.findViewById(R.id.tvCartItemPrice)
        val tvQty: TextView = v.findViewById(R.id.tvCartItemQty)
        val tvSubtotal: TextView = v.findViewById(R.id.tvCartItemSubtotal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cart_row, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = cartItems[position]
        val subtotal = item.price * item.qty

        holder.tvName.text = item.name
        holder.tvQty.text = "x${item.qty}"
        holder.tvPrice.text = "@ ${formatRupiah(item.price)}"
        holder.tvSubtotal.text = formatRupiah(subtotal)
    }

    override fun getItemCount(): Int = cartItems.size

    private fun formatRupiah(number: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(number)
    }
}