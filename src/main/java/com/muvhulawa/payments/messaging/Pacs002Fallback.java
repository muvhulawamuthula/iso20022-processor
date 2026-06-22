package com.muvhulawa.payments.messaging;

import com.muvhulawa.payments.domain.model.ReasonCode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Builds a minimal pacs.002 RJCT when the inbound message is too malformed to run through the
 * XSLT (which needs a schema-valid source). We still owe the sender a structured status — a
 * 500 with a stack trace is never an acceptable answer to a payment counterparty.
 */
final class Pacs002Fallback {
    private Pacs002Fallback() { }

    static String schemaReject(ReasonCode reasonCode) {
        String now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
        return """
               <?xml version="1.0" encoding="UTF-8"?>
               <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10">
                 <FIToFIPmtStsRpt>
                   <GrpHdr>
                     <MsgId>STS-REJECT-%s</MsgId>
                     <CreDtTm>%s</CreDtTm>
                   </GrpHdr>
                   <OrgnlGrpInfAndSts>
                     <OrgnlMsgNmId>pacs.008.001.08</OrgnlMsgNmId>
                     <GrpSts>RJCT</GrpSts>
                     <StsRsnInf><Rsn><Cd>%s</Cd></Rsn></StsRsnInf>
                   </OrgnlGrpInfAndSts>
                 </FIToFIPmtStsRpt>
               </Document>
               """.formatted(now, now, reasonCode.name());
    }
}
