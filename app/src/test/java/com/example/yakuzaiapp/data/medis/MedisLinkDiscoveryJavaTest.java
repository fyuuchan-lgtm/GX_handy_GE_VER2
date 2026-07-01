package com.example.yakuzaiapp.data.medis;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MedisLinkDiscoveryJavaTest {
    @Test
    public void discoverSelectsLatestLinksWithIndependentDates() {
        MedisDownloadLinks links = MedisLinkDiscovery.INSTANCE.discover(
                "https://www2.medis.or.jp/hcode/",
                "<a href=\"/hcode/moto_data/h20250531_h.zip\">old</a>"
                        + "<a href=\"/hcode/moto_data/h20260531_h.zip\">new</a>",
                "https://medhot.medd.jp/view_download",
                "<a href=\"/csv/A_20260430_2.txt;jsessionid=abc\">old</a>"
                        + "<a href=\"/csv/A_20260615_2.txt;jsessionid=def\">new</a>"
        );

        assertEquals("20260531", links.getHotVersionDate());
        assertEquals("https://www2.medis.or.jp/hcode/moto_data/h20260531_h.zip", links.getHotZipUrl());
        assertEquals("20260615", links.getSalesVersionDate());
        assertEquals("https://medhot.medd.jp/csv/A_20260615_2.txt;jsessionid=def", links.getSalesFileUrl());
    }

    @Test(expected = IllegalStateException.class)
    public void discoverFailsWhenHotLinkIsMissing() {
        MedisLinkDiscovery.INSTANCE.discover(
                "https://www2.medis.or.jp/hcode/",
                "<html></html>",
                "https://medhot.medd.jp/view_download",
                "<a href=\"/csv/A_20260615_2.txt\">sales</a>"
        );
    }
}
