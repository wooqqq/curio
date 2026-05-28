package com.curio.service;

import com.curio.dto.item.OgData;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OgCrawlerService {

    private static final int TIMEOUT_MS = 5000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; CurioBot/1.0; +https://mycurio.kr)";

    public OgData crawl(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            String title = meta(doc, "og:title");
            if (title == null) title = meta(doc, "twitter:title");
            if (title == null) title = doc.title();

            String thumbnail = meta(doc, "og:image");
            if (thumbnail == null) thumbnail = meta(doc, "twitter:image");

            String description = meta(doc, "og:description");
            if (description == null) description = meta(doc, "twitter:description");

            return new OgData(trim(title, 500), trim(thumbnail, 1000), trim(description, 1000));
        } catch (Exception e) {
            log.warn("OG crawl failed for {}: {}", url, e.getMessage());
            return new OgData(url, null, null);
        }
    }

    private String meta(Document doc, String property) {
        String val = doc.select("meta[property=" + property + "]").attr("content");
        if (!val.isBlank()) return val;
        val = doc.select("meta[name=" + property + "]").attr("content");
        return val.isBlank() ? null : val;
    }

    private String trim(String value, int maxLen) {
        if (value == null) return null;
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}