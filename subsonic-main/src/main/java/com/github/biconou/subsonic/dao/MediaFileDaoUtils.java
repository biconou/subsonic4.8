/**
 * Paquet de définition
 **/
package com.github.biconou.subsonic.dao;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.sun.istack.Nullable;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.HppcMaps;
import org.elasticsearch.search.SearchHit;
import com.github.biconou.dao.ElasticSearchClient;
import net.sourceforge.subsonic.domain.MediaFile;

/**
 * Description: Merci de donner une description du service rendu par cette classe
 */
public class MediaFileDaoUtils {

  protected static SearchResponse searchMediaFileByPath(ElasticSearchClient client,String path) {

    String jsonSearch = "{" +
      "    \"constant_score\" : {" +
      "        \"filter\" : {" +
      "            \"term\" : {" +
      "                \"path\" : \""+preparePathForSearch(path)+"\"" +
      "            }" +
      "        }" +
      "    }" +
      "}";

    return client.getClient().prepareSearch(ElasticSearchClient.SUBSONIC_MEDIA_INDEX_NAME)
      .setQuery(jsonSearch).setVersion(true).execute().actionGet();
  }

  protected static MediaFile convertFromHit(ElasticSearchClient client,SearchHit hit) throws RuntimeException {
    MediaFile mediaFile = null;

    if (hit != null) {
      String hitSource = hit.getSourceAsString();
      try {
        mediaFile = client.getMapper().readValue(hitSource, MediaFile.class);
        mediaFile.setESId(hit.id());
      } catch (IOException e) {
        throw new RuntimeException("Error while reading MediaFile object from index. ", e);
      }
    }
    return mediaFile;
  }

  public static List<MediaFile> extractMediaFiles(ElasticSearchClient client, SearchRequestBuilder searchRequestBuilder, @Nullable Integer from, @Nullable Integer size) {
    if (from != null) {
      searchRequestBuilder.setFrom(from);
    }
    if (size != null) {
      searchRequestBuilder.setSize(size);
    }

    SearchResponse response = searchRequestBuilder.execute().actionGet();

    List<MediaFile> returnedSongs = Arrays.stream(response.getHits().getHits()).map(hit -> convertFromHit(client,hit)).collect(Collectors.toList());

    return returnedSongs;
  }

  public static List<MediaFile> extractMediaFiles(ElasticSearchClient client, String jsonSearch, @Nullable Integer from, @Nullable Integer size) {

    SearchRequestBuilder searchRequestBuilder = client.getClient().prepareSearch(ElasticSearchClient.SUBSONIC_MEDIA_INDEX_NAME)
            .setQuery(jsonSearch).setVersion(true);

    return extractMediaFiles(client,searchRequestBuilder,from,size);

  }

  public static String preparePathForSearch(String path) {
    return path.replace("\\","\\\\");
  }
}
 
