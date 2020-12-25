package awais.instagrabber.asyncs;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.HttpURLConnection;
import java.net.URL;

import awais.instagrabber.BuildConfig;
import awais.instagrabber.interfaces.FetchListener;
import awais.instagrabber.utils.Constants;
import awais.instagrabber.utils.NetworkUtils;
import awais.instagrabber.utils.TextUtils;
import awais.instagrabber.utils.Utils;
import awaisomereport.LogCollector;

import static awais.instagrabber.utils.Utils.logCollector;

public final class ProfilePictureFetcher extends AsyncTask<Void, Void, String> {
    private final FetchListener<String> fetchListener;
    private final String userName, userId, picUrl;
    private final boolean isHashtag;

    public ProfilePictureFetcher(final String userName, final String userId, final FetchListener<String> fetchListener,
                                 final String picUrl, final boolean isHashtag) {
        this.fetchListener = fetchListener;
        this.userName = userName;
        this.userId = userId;
        this.picUrl = picUrl;
        this.isHashtag = isHashtag;
    }

    @Override
    protected String doInBackground(final Void... voids) {
        String out = null;
        if (isHashtag) out = picUrl;
        else if (Utils.settingsHelper.getBoolean(Constants.INSTADP)) try {
            final HttpURLConnection backup =
                    (HttpURLConnection) new URL("https://instadp.com/fullsize/" + userName).openConnection();
            backup.setUseCaches(false);
            backup.setRequestMethod("GET");
            backup.setRequestProperty("User-Agent", Constants.A_USER_AGENT);

            final String instadp = backup.getResponseCode() == HttpURLConnection.HTTP_OK ? NetworkUtils.readFromConnection(backup) : null;
            backup.disconnect();

            if (!TextUtils.isEmpty(instadp)) {
                final Document doc = Jsoup.parse(instadp);
                boolean fallback = false;

                final int imgIndex = instadp.indexOf("preloadImg('"), lastIndex;

                Element element = doc.selectFirst(".instadp");
                if (element != null && (element = element.selectFirst(".picture")) != null)
                    out = element.attr("src");
                else if ((element = doc.selectFirst(".download-btn")) != null)
                    out = element.attr("href");
                else if (imgIndex != -1 && (lastIndex = instadp.indexOf("')", imgIndex)) != -1)
                    out = instadp.substring(imgIndex + 12, lastIndex);
                else {
                    final Elements imgs = doc.getElementsByTag("img");
                    for (final Element img : imgs) {
                        final String imgStr = img.toString();
                        if (imgStr.contains("cdninstagram.com")) out = img.attr("src");
                    }
                }
            }
            if (TextUtils.isEmpty(out)) out = picUrl;
        } catch (final Exception e) {
            if (logCollector != null)
                logCollector.appendException(e, LogCollector.LogFile.ASYNC_PROFILE_PICTURE_FETCHER, "doInBackground");
            if (BuildConfig.DEBUG) Log.e("AWAISKING_APP", "", e);
        }

        return out;
    }

    @Override
    protected void onPreExecute() {
        if (fetchListener != null) fetchListener.doBefore();
    }

    @Override
    protected void onPostExecute(final String result) {
        if (fetchListener != null) fetchListener.onResult(result);
    }
}