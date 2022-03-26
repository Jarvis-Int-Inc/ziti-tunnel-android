/*
 * Copyright (c) 2020 NetFoundry. All rights reserved.
 */

package org.openziti.mobile.net

import android.util.Log
import org.openziti.net.dns.DNSResolver
import org.pcap4j.packet.DnsPacket
import org.pcap4j.packet.DnsRDataA
import org.pcap4j.packet.DnsRDataAaaa
import org.pcap4j.packet.DnsResourceRecord
import org.pcap4j.packet.namednumber.DnsOpCode
import org.pcap4j.packet.namednumber.DnsRCode
import org.pcap4j.packet.namednumber.DnsResourceRecordType
import org.pcap4j.util.ByteArrays
import org.xbill.DNS.*
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException


class DNS(val dnsResolver: DNSResolver) {

    fun resolve(packet: DnsPacket): DnsPacket {
        val q = packet.header.questions.firstOrNull()

        // It appears that Ziti requests are handled here. Non-Ziti requests get passed on to
        // bypassDNS, although they are still resolved in the class.

        val resp = q?.let {
            when(it.qType) {
                DnsResourceRecordType.A -> {
                    val answer = DnsResourceRecord.Builder()
                            .dataType(it.qType)
                            .dataClass(it.qClass)
                            .name(it.qName)
                            .ttl(30)
                            .rdLength(ByteArrays.INET4_ADDRESS_SIZE_IN_BYTES.toShort())

                    val ip = dnsResolver.resolve(it.qName.name) ?: bypassDNS(it.qName.name, it.qType)

                    if (ip != null) {
                        val rdata = DnsRDataA.Builder()
                                .address(ip as Inet4Address).build()
                        answer.rData(rdata)
                    }

                    answer.build()
                }

                DnsResourceRecordType.AAAA -> {
                    val answer = DnsResourceRecord.Builder()
                            .dataType(it.qType)
                            .dataClass(it.qClass)
                            .name(it.qName)
                            .ttl(30)
                            .rdLength(ByteArrays.INET6_ADDRESS_SIZE_IN_BYTES.toShort())


                    val ip = bypassDNS(it.qName.name, it.qType)

                    if (ip != null) {
                        val rdata = DnsRDataAaaa.Builder()
                                .address(ip as Inet6Address).build()
                        answer.rData(rdata)
                    }

                    answer.build()
                }

                else -> {
                    Log.d("DNS", "request ${it.qType} ${it.qName}")
                    null
                }
            }
        }


        val rb = DnsPacket.Builder()
                .id(packet.header.id)
                .response(true)
                .opCode(DnsOpCode.QUERY)
                .qdCount(packet.header.qdCount)
                .questions(packet.header.questions)

        if(resp == null) {
            rb.rCode(DnsRCode.NX_DOMAIN)
                    .anCount(0)
                    .answers(emptyList())
        } else {
            rb.rCode(DnsRCode.NO_ERROR)
                    .anCount(1)
                    .answers(listOf(resp))
        }

        return rb.build()
    }

    private fun bypassDNS(name: String, type: DnsResourceRecordType): InetAddress? {

        // This handles non-Ziti DNS requests. Normally this would be handled by
        // java.net using InetAddress. The problem is that this uses the DNS set in
        // Android. On paper you can change that using System.properties, but that
        // doesn't seem to work (a limitation others have written about online).
        // Accordingly, I'm using DNSJava, an OS DNS library for Java that allows you
        // to specify the server. Normally I would then kill the old code, but for
        // now I'm leaving it in place as I might choose to use that for whitelisted
        // domains.

        val queryRecord: Record =
            Record.newRecord(Name.fromString("$name."), Type.A, DClass.IN)
        val queryMessage: Message = Message.newQuery(queryRecord)

        // Create the resolver, set in this instance to Noah's DNS. Yes, hardcoding
        // it is a dumb idea, but this is a proof of concept, and I pray that it isn't
        // still this way in a final release. If it is, feel free to point out my
        // stupidity. (I'll probably just blame Jeremy, though, 'cause I can).

        val r: Resolver = SimpleResolver("3.237.5.111")

        // Let's give it a shot.

        try {
            // This resolves it on the current thread. Not normally a good idea, but i want to
            // stick to the existing approach rather than try something new.

            val response = r.send(queryMessage)
            val answers = response.getSection(Section.ANSWER)

            // An array of answers may be stored in the response. I'm only expecting one, but I'll
            // cycle through the array just in case.

            for (a in answers) {
                // Looking for an A or AAAA record type.
                if (type == DnsResourceRecordType.A && a is ARecord) {
                    Log.d("DNS", "Found ${a.address.hostAddress} for $name")

                    // This is ugly. But currently Noah's DNS returns its own IP when it denies
                    // a domain name. Thus if the IP (and it will always be IPV4) is equal to
                    // Noah's, we can log it as a block event.
                    if (a.address.hostAddress.equals("3.237.5.111"))
                        Log.d("DNS", "DN $name blocked")

                    return a.address
                }
                if (type == DnsResourceRecordType.AAAA && a is AAAARecord) {
                    Log.d("DNS", "Found ${a.address.hostAddress} for $name")
                    return a.address
                }
            }
        }
        catch(ex: Exception) {
            // Ignore
        }

        return null

        // Original code. Kept because I expect to use this for whitelisted domains.

        /*
        try {

            // Kill this later
            for (a in InetAddress.getAllByName(name)) {
                Log.d("DNS", "Returned $a");
            }
            for (a in InetAddress.getAllByName(name)) {
                Log.d("DNS", "Returned $a");
                if (type == DnsResourceRecordType.A && a is Inet4Address) return a
                if (type == DnsResourceRecordType.AAAA && a is Inet6Address) return a
            }
        } catch(ignored: UnknownHostException) {
        }

        return null
        */
    }
}