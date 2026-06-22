package com.muvhulawa.payments.domain.model;

/**
 * ISO 20022 external status reason codes (ExternalStatusReason1Code subset).
 * Using the real code set — not ad-hoc strings — is what makes a rejection
 * machine-reconcilable on the sender's side.
 */
public enum ReasonCode {
    FF01("Invalid file format / schema-invalid message"),
    AM02("Amount not allowed (zero or negative settlement amount)"),
    AM03("Currency not allowed / unsupported"),
    AM05("Duplication — message already processed"),
    NARR("See narrative (debtor and creditor are the same party)");

    private final String description;
    ReasonCode(String description) { this.description = description; }
    public String description() { return description; }
}
