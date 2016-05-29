package com.github.biconou.subsonic.service;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import junit.framework.Assert;
import net.sourceforge.subsonic.dao.AlbumDao;
import net.sourceforge.subsonic.dao.MediaFileDao;
import net.sourceforge.subsonic.domain.Album;
import net.sourceforge.subsonic.domain.MediaFile;
import net.sourceforge.subsonic.domain.MusicFolder;
import net.sourceforge.subsonic.service.SearchService;
import org.springframework.context.ApplicationContext;
import com.github.biconou.subsonic.TestCaseUtils;
import junit.framework.TestCase;
import net.sourceforge.subsonic.dao.MusicFolderDao;

/**
 * Created by remi on 01/05/2016.
 */
public class PerformanceServiceTestCase extends TestCase {

  private static String baseResources = "/com/github/biconou/subsonic/service/performanceServiceTestCase/";

  private final MetricRegistry metrics = new MetricRegistry();
  private ConsoleReporter reporter = null;
  private JmxReporter jmxReporter = null;


  private MediaFileDao mediaFileDao = null;
  private AlbumDao albumDao = null;
  private MusicFolderDao musicFolderDao = null;
  private SearchService searchService = null;
  private MediaScannerService mediaScannerService = null;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    TestCaseUtils.setSubsonicHome(baseResources);

    reporter = ConsoleReporter.forRegistry(metrics)
            .convertRatesTo(TimeUnit.SECONDS.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build();

    jmxReporter = JmxReporter.forRegistry(metrics).build();
    jmxReporter.start();


    // Prepare database
    TestCaseUtils.prepareDataBase(baseResources);

    // load spring context
    ApplicationContext context = TestCaseUtils.loadSpringApplicationContext(baseResources);

    mediaFileDao = (MediaFileDao) context.getBean("mediaFileDao");
    albumDao = (AlbumDao) context.getBean("albumDao");
    musicFolderDao = (MusicFolderDao) context.getBean("musicFolderDao");
    searchService = (SearchService) context.getBean("searchService");
    mediaScannerService = (MediaScannerService) context.getBean("mediaScannerService");

    TestCaseUtils.execScan(mediaScannerService);

  }


  /**
   *
   */
  public void testPerformanceRandomAlbumBrowse() {

    Timer globalTimer = metrics.timer(MetricRegistry.name("randomAlbumBrowse", "Timer.global"));
    Timer loopTimer = metrics.timer(MetricRegistry.name("randomAlbumBrowse", "Timer.loop"));
    Timer fetchSongsTimer = metrics.timer(MetricRegistry.name("randomAlbumBrowse", "Timer.fetchSongs"));
    Timer randomAlbumsTimer = metrics.timer(MetricRegistry.name("randomAlbumBrowse", "Timer.randomAlbums"));

    Timer.Context globalTimerContext = globalTimer.time();

    int i = 0;
    while (i <= 1000) {

      final boolean fisrtIteration = i == 0;

      Timer.Context loopTimerContext = null;
      if (!fisrtIteration) {
        loopTimerContext = loopTimer.time();
      }

      Timer.Context randomAlbumsTimerContext = null;
      // We do not take in consideration the time of the first iteration
      // because it could be much more slow than the other ones
      if (!fisrtIteration) {
        randomAlbumsTimerContext = randomAlbumsTimer.time();
      }
      List<MediaFile> foundAlbums = searchService.getRandomAlbums(10, musicFolderDao.getAllMusicFolders());
      if (!fisrtIteration) {
        randomAlbumsTimerContext.stop();
      }

      foundAlbums.stream().forEach(album -> {

        Timer.Context fetchSongsTimerContext = null;
        if (!fisrtIteration) {
          fetchSongsTimerContext = fetchSongsTimer.time();
        }
        mediaFileDao.getSongsForAlbum(album.getArtist(), album.getAlbumName());
        if (!fisrtIteration) {
          fetchSongsTimerContext.stop();
        }
      });

      if (!fisrtIteration) {
        loopTimerContext.stop();
      }
      i++;
    }

    globalTimerContext.stop();
    reporter.report();
    System.out.print("End");
  }

  /**
   *
   */
  public void testPerformanceDirectoriesBrowse() {

    Timer globalTimer = metrics.timer(MetricRegistry.name("directoriesBrowse", "Timer.global"));
    Timer getChildrenTimer = metrics.timer(MetricRegistry.name("directoriesBrowse", "Timer.getChildren"));

    Timer.Context globalTimerContext = globalTimer.time();

    musicFolderDao.getAllMusicFolders().forEach(musicFolder -> {
      File musicFolderPath = musicFolder.getPath();
      mediaFileDao.getChildrenOf(musicFolderPath.getPath()).forEach(mediaFile -> {
        browseMediaFile(mediaFile, getChildrenTimer);
      });

    });


    globalTimerContext.stop();
    reporter.report();
    System.out.print("End");
  }

  /**
   * @param mediaFile
   */
  private void browseMediaFile(MediaFile mediaFile, Timer timer) {
    if (mediaFile.getMediaType().equals(MediaFile.MediaType.ALBUM) || mediaFile.getMediaType().equals(MediaFile.MediaType.DIRECTORY)) {
      Timer.Context context = timer.time();
      mediaFileDao.getChildrenOf(mediaFile.getPath()).forEach(mediaFile1 -> browseMediaFile(mediaFile1, timer));
      context.stop();
    }
  }

}
