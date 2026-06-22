package com.muvhulawa.payments.messaging.jaxb;

import jakarta.xml.bind.annotation.*;
import java.math.BigDecimal;

/** Maps &lt;IntrBkSttlmAmt Ccy="ZAR"&gt;15000.00&lt;/IntrBkSttlmAmt&gt; — value + currency attribute. */
@XmlAccessorType(XmlAccessType.FIELD)
public class ActiveCurrencyAndAmount {

    @XmlValue
    private BigDecimal value;

    @XmlAttribute(name = "Ccy", required = true)
    private String ccy;

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal v) { this.value = v; }
    public String getCcy() { return ccy; }
    public void setCcy(String v) { this.ccy = v; }
}
