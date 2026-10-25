package com.example.data.model

data class OpenCodeModelFeature(
  val hasWeb: Boolean = false,
  val hasTerminal: Boolean = false,
  val hasText: Boolean = true,
  val hasVision: Boolean = false,
  val hasAudio: Boolean = false,
  val extraBadge: String? = null
)

data class OpenCodeModel(
  val id: String,
  val displayName: String,
  val isFree: Boolean = true,
  val isDefault: Boolean = false,
  val features: OpenCodeModelFeature = OpenCodeModelFeature(),
  val inputCost: String = "$0.00",
  val outputCost: String = "$0.00",
  val cacheReadCost: String = "$0.00",
  val cacheWriteCost: String = "-",
  val isEnabled: Boolean = true,
  val description: String = ""
)
