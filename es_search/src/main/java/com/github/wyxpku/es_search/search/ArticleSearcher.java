package com.github.wyxpku.es_search.search;

/**
 * Created by wyx on 2017/2/20.
 */

import com.github.wyxpku.es_search.data.Constants;
import com.antbrains.nlp.wordseg.SentenceSplit;
import com.antbrains.nlp.wordseg.WordSeg;
import com.google.gson.Gson;
import org.apache.log4j.Logger;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;

public class ArticleSearcher {
    protected static Logger logger = Logger.getLogger(ArticleSearcher.class);

    private Client client;
    private WordSeg ws;

    public ArticleSearcher(String clusterName, String hostName) throws Exception {
        Settings settings = Settings.settingsBuilder()
                .put("cluster.name", clusterName).build();
        client = TransportClient.builder().settings(settings).build()
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(hostName), 9300));

        ws = WordSeg.getInstance();
    }

    public SearchResult search(String s, float titleBoost, int pageNo, String startTag, String endTag) {
        if (pageNo < 1 || pageNo > 1000) pageNo = 1;
        if (s.isEmpty()) return SearchResult.EMPTY;

        List<String> words = ws.mmRmmSeg(s);
        SearchRequestBuilder srb = client.prepareSearch(Constants.indexName).setTypes(Constants.TYPE_ARTICLE);
        BoolQueryBuilder bqb = QueryBuilders.boolQuery();
        for (String word : words) {
            word = word.toLowerCase();
            BoolQueryBuilder bqb2 = QueryBuilders.boolQuery();
            bqb2.should(QueryBuilders.termQuery(Constants.PROPERTY_SEG_TITLE, word).boost(titleBoost));
            bqb2.should(QueryBuilders.termQuery(Constants.PROPERTY_SEG_CONTENT, word));
            bqb.must(bqb2);
        }
        //System.out.println(bqb.toString());
        srb.addSort("pubTime", SortOrder.DESC);
        srb.setQuery(bqb);
        srb.setFrom((pageNo - 1) * 10);
        srb.setSize(10);

        // add source and main-image
        srb.addFields(new String[]{"title", "pubTime", "url", "types", "content", "source", "mainImage",});
        //System.out.println(srb.toString());
        SearchResponse searchResponse = srb.execute().actionGet();
        SearchHit[] hits = searchResponse.getHits().getHits();
        SearchResult sr = new SearchResult();
        for (SearchHit hit : hits) {
            String url = "NULL";
            String title = "NULL";
            String pubTime = "NULL";
            List<Object> types;
            String content = "NULL";

            // source
            String src = "NULL";

            // main-image
            String mainImage = "http://search.chanlin.org/static/img/default-mainimg.png";
            try {
                url = hit.getFields().get("url").getValue();
            } catch (Exception e) {
                logger.error("URL not found!!!");
                logger.error(e.getMessage());
            }
            try {
                title = hit.getFields().get("title").getValue();
            } catch (Exception e) {
                logger.error("title not found!!!, URL: " + url);
                logger.error(e.getMessage());
            }
            try {
                pubTime = hit.getFields().get("pubTime").getValue();
            } catch (Exception e) {
                logger.error("pubtime not found!!!, URL: " + url);
                logger.error(e.getMessage());
            }
            try {
                types = hit.getFields().get("types").getValues();
            } catch (Exception e) {
                logger.error("types not found!!!, URL: " + url);
                logger.error(e.getMessage());
            }
            try {
                content = hit.getFields().get("content").getValue();
            } catch (Exception e) {
                logger.error("content not found!!!, URL: " + url);
                logger.error(e.getMessage());
            }
            try {
                src = hit.getFields().get("source").getValue();
            } catch (Exception e) {
                logger.error("src not found!!!, URL: " + url);
                logger.error(e.getMessage());
            }
            try {
                mainImage = hit.getFields().get("mainImage").getValue();
            } catch (Exception e) {
                logger.error("mainImage not found!!!, URL: " + url);
                logger.error(e.getMessage());
            }
            SearchItem item = new SearchItem();
            item.setUrl(url);
            item.setPubTime(pubTime);
            item.setContent(content);
            item.setTitle(title);
            item.setTitleHighlight(this.highlightTitle(title, words, startTag, endTag));
            item.setContentHightlight(this.highlightConent(content, words, startTag, endTag));

            // set source and main-image
            item.setSrc(src);
            item.setMainImage(mainImage);
            sr.getItems().add(item);
        }
        sr.setTotalResult(searchResponse.getHits().totalHits());
        return sr;
    }
    public SearchResult hot_inrange(Date startDate, Date endDate){
        if (startDate == null)
            return SearchResult.EMPTY;

        String startTime = date2str(startDate);
        String endTime = null;
        if (endDate == null){
            endTime = getTomorrow(startDate);
        } else {
            endTime = getTomorrow(endDate);
        }
        SearchRequestBuilder srb = client.prepareSearch(Constants.indexName).setTypes(Constants.TYPE_ARTICLE);

        RangeQueryBuilder qb = new RangeQueryBuilder("pubTime").from(startTime).to(endTime);
        //System.out.println(bqb.toString());
        srb.addSort("pubTime", SortOrder.DESC);
        srb.setQuery(qb);

        // add source and main-image
        srb.addFields(new String[]{"title", "url"});
        //System.out.println(srb.toString());
        SearchResponse searchResponse = srb.execute().actionGet();
        SearchHit[] hits = searchResponse.getHits().getHits();
        SearchResult sr = new SearchResult();
        for (SearchHit hit: hits){
            String url = "NULL";
            String title = "NULL";
            try {
                url = hit.getFields().get("url").getValue();
            } catch (Exception e) {
                logger.error("URL not found!!!");
                logger.error(e.getMessage());
            }
            try {
                title = hit.getFields().get("title").getValue();
            } catch (Exception e) {
                logger.error("title not found!!!, URL: " + url);
                logger.error(e.getMessage());
            }
            SearchItem item = new SearchItem();
            item.setUrl(url);
            item.setTitle(title);
            sr.getItems().add(item);
        }
        return sr;
    }
    private String date2str(Date date){
        if (date == null)
            return null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }
    private String getTomorrow(Date date){
        if (date == null)
            return null;
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        cal.setTime(date);
        cal.set(cal.DATE, cal.get(cal.DATE) + 1);
        return sdf.format(cal.getTime());
    }

    private String highlightTitle(String title, List<String> words, String startTag, String endTag) {
        Set<String> searchWords = new HashSet<>();
        for (String word : words) {
            searchWords.add(word.toLowerCase());
        }
        List<String> titleWords = ws.mmRmmSeg(title);
        StringBuilder sb = new StringBuilder("");
        for (String titleWord : titleWords) {
            if (searchWords.contains(titleWord.toLowerCase())) {
                sb.append(startTag);
                sb.append(titleWord);
                sb.append(endTag);
            } else {
                sb.append(titleWord);
            }
        }
        return sb.toString();
    }

    private String highlightConent(String content, List<String> words, String startTag, String endTag) {
        String[] sens = SentenceSplit.splitSentences(content);
        Set<String> searchWords = new HashSet<>();
        for (String word : words) {
            searchWords.add(word.toLowerCase());
        }
        List<Object[]> list = new ArrayList<>(sens.length);
        int order = 0;
        for (String sen : sens) {
            List<String> senWords = ws.mmRmmSeg(sen);
            int matchWords = 0;
            StringBuilder sb = new StringBuilder("");
            for (String senWord : senWords) {
                if (searchWords.contains(senWord.toLowerCase())) {
                    matchWords++;
                    sb.append(startTag);
                    sb.append(senWord);
                    sb.append(endTag);
                } else {
                    sb.append(senWord);
                }
            }
            list.add(new Object[]{matchWords, sb.toString(), order});
            order++;
        }

        Collections.sort(list, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] o1, Object[] o2) {
                Integer c1 = (Integer) o1[0];
                Integer c2 = (Integer) o2[0];
                return c2.compareTo(c1);
            }
        });

        List<Object[]> subList = list.subList(0, Math.min(3, list.size()));
        Collections.sort(subList, new Comparator<Object[]>() {

            @Override
            public int compare(Object[] o1, Object[] o2) {
                Integer order1 = (Integer) o1[2];
                Integer order2 = (Integer) o2[2];
                return order1.compareTo(order2);
            }

        });

        StringBuilder sb = new StringBuilder("");

        for (Object[] arr : subList) {
            sb.append((String) arr[1] + "...");
        }

        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("need 2 args: clusterName hostName");
            System.exit(-1);
        }
        ArticleSearcher searcher = new ArticleSearcher(args[0], args[1]);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, "UTF8"));
        String line;
        System.out.println("Enter query");
        Gson gson = new Gson();
        while ((line = br.readLine()) != null) {
            SearchResult sr = searcher.search(line, 5.0f, 1, "<font color='red'>", "</font>");
            System.out.println(gson.toJson(sr));
        }
    }
}