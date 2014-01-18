package net.lightbody.bmp.proxy;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.List;

import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarLog;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.Test;

public class HarResultsTest extends DummyServerTest {
	/**
	 * Check that the sizes recorded in the HAR represent the traffic from
	 * proxy to web server, and not traffic from the client to the proxy.
	 */
    @Test
    public void testRequestAndResponseSizesAreSet() throws Exception {

        // see https://github.com/lightbody/browsermob-proxy/pull/36 for history

        proxy.setCaptureContent(true);
        proxy.newHar("test");

        HttpGet get = new HttpGet("http://127.0.0.1:8080/a.txt");
        // calculate expected header size for a direct fetch
        long expectedHeaderSize = getExpectedHeaderSize(get);
        client.execute(get);

        Har har = proxy.getHar();
        HarLog log = har.getLog();
        List<HarEntry> entries = log.getEntries();
        HarEntry entry = entries.get(0);

        Assert.assertEquals(100, entry.getRequest().getHeadersSize());
        Assert.assertEquals(0, entry.getRequest().getBodySize());
        assertThat(entry.getResponse().getHeadersSize(), is(expectedHeaderSize));
        Assert.assertEquals(13, entry.getResponse().getBodySize());
    }

    /**
	 * Measures the size of the headers received when making the supplied
	 * request.
	 * <p>
	 * The request is made directly, without proxying. The request is
	 * {@link HttpRequestBase#reset()} before this method returns, so the
	 * request can be reused.
	 * <p>
	 * The size of the response headers is: The size of the status line, then 4
	 * bytes for the CRLF and blank line (extra CRLF) that separates the status
	 * from the headers, then the size of each header line, plus 2 bytes per
	 * header for the CRLF sequence.
	 * 
	 * @throws IOException
	 * @throws ClientProtocolException
	 */
    private long getExpectedHeaderSize(HttpRequestBase request) throws ClientProtocolException, IOException {
		try {
			// 2 represents the size of the CRLF that terminates each line
			HttpResponse response = getUnproxiedHttpClient().execute(request);
			long size = 0;
			size += response.getStatusLine().toString().length() + 2; // status 
			size += 2; // blank line
			for (Header h : response.getAllHeaders()) { // header lines
				size += h.toString().length() + 2;
			}
			return size;
		} finally {
			request.reset();
		}
    }

    /**
     * Return a HttpClient that doesn't use the proxy. Useful for determining
     * expected results. 
     */
    private DefaultHttpClient getUnproxiedHttpClient() {
    	DefaultHttpClient httpClient = getNewHttpClient();
    	httpClient.getParams().removeParameter(ConnRoutePNames.DEFAULT_PROXY);
    	return httpClient;
    }
}
