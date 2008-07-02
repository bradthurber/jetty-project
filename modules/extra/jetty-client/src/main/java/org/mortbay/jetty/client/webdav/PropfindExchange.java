package org.mortbay.jetty.client.webdav;

import org.mortbay.io.Buffer;
import org.mortbay.jetty.client.CachedExchange;
import org.mortbay.jetty.client.HttpExchange;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


public class PropfindExchange extends HttpExchange
{
    boolean _propertyExists = false;
    private boolean _isComplete = false;

    /* ------------------------------------------------------------ */
    protected void onResponseStatus(Buffer version, int status, Buffer reason) throws IOException
    {
        if ( status == HttpServletResponse.SC_OK )
        {
            System.err.println( "PropfindExchange:Status: Exists" );
            _propertyExists = true;
        }
        else
        {
            System.err.println( "PropfindExchange:Status: Not Exists" );
        }

        super.onResponseStatus(version, status, reason);
    }

    public boolean exists()
    {
        return _propertyExists;
    }

    public void waitTilCompletion() throws InterruptedException
    {
        synchronized (this)
        {
            while ( !_isComplete)
            {
                this.wait();
            }
        }
    }

    protected void onResponseComplete() throws IOException
    {
        _isComplete = true;

        super.onResponseComplete();
    }

}