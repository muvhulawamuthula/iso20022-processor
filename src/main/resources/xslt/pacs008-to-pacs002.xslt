<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:p8="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08"
    xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10"
    exclude-result-prefixes="p8">
  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <!-- Group status derived by the Java side: ACSC, PART or RJCT. -->
  <xsl:param name="grpStatus">ACSC</xsl:param>
  <xsl:param name="creDtTm">1970-01-01T00:00:00</xsl:param>

  <!-- Per-transaction verdicts, resolved in-process (never fetched) to:
       <verdicts><v id="TxId" sts="ACSC|RJCT" rsn="AM02"/>...</verdicts> -->
  <xsl:variable name="verdicts" select="document('verdicts:current')"/>

  <xsl:template match="/p8:Document">
    <Document>
      <FIToFIPmtStsRpt>
        <GrpHdr>
          <MsgId>STS-<xsl:value-of select="p8:FIToFICstmrCdtTrf/p8:GrpHdr/p8:MsgId"/></MsgId>
          <CreDtTm><xsl:value-of select="$creDtTm"/></CreDtTm>
        </GrpHdr>
        <OrgnlGrpInfAndSts>
          <OrgnlMsgId><xsl:value-of select="p8:FIToFICstmrCdtTrf/p8:GrpHdr/p8:MsgId"/></OrgnlMsgId>
          <OrgnlMsgNmId>pacs.008.001.08</OrgnlMsgNmId>
          <OrgnlNbOfTxs><xsl:value-of select="count(p8:FIToFICstmrCdtTrf/p8:CdtTrfTxInf)"/></OrgnlNbOfTxs>
          <GrpSts><xsl:value-of select="$grpStatus"/></GrpSts>
        </OrgnlGrpInfAndSts>
        <xsl:for-each select="p8:FIToFICstmrCdtTrf/p8:CdtTrfTxInf">
          <xsl:variable name="txid" select="p8:PmtId/p8:TxId"/>
          <xsl:variable name="v" select="$verdicts//v[@id = $txid]"/>
          <TxInfAndSts>
            <OrgnlInstrId><xsl:value-of select="p8:PmtId/p8:InstrId"/></OrgnlInstrId>
            <OrgnlEndToEndId><xsl:value-of select="p8:PmtId/p8:EndToEndId"/></OrgnlEndToEndId>
            <OrgnlTxId><xsl:value-of select="$txid"/></OrgnlTxId>
            <TxSts><xsl:value-of select="$v/@sts"/></TxSts>
            <xsl:if test="string($v/@rsn) != ''">
              <StsRsnInf><Rsn><Cd><xsl:value-of select="$v/@rsn"/></Cd></Rsn></StsRsnInf>
            </xsl:if>
          </TxInfAndSts>
        </xsl:for-each>
      </FIToFIPmtStsRpt>
    </Document>
  </xsl:template>
</xsl:stylesheet>
