package com.example.smsaccounting

enum class TxType { INCOME, EXPENSE }

/** مبلغ‌ها همیشه به ریال ذخیره می‌شوند */
data class Tx(
    val id: Long = 0,
    val hash: String,
    val sender: String,
    val type: TxType,
    val amount: Long,
    val balance: Long?,
    val merchant: String,
    val mkey: String,
    val category: String,
    val ts: Long,
    val body: String,
)
