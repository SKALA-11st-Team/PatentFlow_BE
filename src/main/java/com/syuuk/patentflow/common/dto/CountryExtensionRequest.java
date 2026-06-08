package com.syuuk.patentflow.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CountryExtensionRequest(@Min(0) @Max(240) int extensionMonths) {}
