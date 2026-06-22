package com.muvhulawa.payments.domain.validation;

import com.muvhulawa.payments.domain.model.ReasonCode;

/** A business-rule failure carrying the ISO reason code to surface in the pacs.002. */
public record BusinessRuleViolation(ReasonCode reasonCode, String narrative) { }
