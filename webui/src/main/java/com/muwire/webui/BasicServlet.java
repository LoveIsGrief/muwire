// ========================================================================
// Copyright 199-2004 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package com.muwire.webui;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.i2p.I2PAppContext;
import net.i2p.data.ByteArray;
import net.i2p.data.DataHelper;
import net.i2p.servlet.util.WriterOutputStream;
import net.i2p.util.ByteCache;
import net.i2p.util.Log;
import net.i2p.util.SecureFile;
import net.i2p.util.SystemVersion;


/* ------------------------------------------------------------ */
/**
 * Based on DefaultServlet from Jetty 6.1.26, heavily simplified
 * and modified to remove all dependencies on Jetty libs.
 *
 * Supports HEAD and GET only, for resources from the .war and local files.
 * Supports files and resource only.
 * Supports MIME types with local overrides and additions.
 * Supports Last-Modified.
 * Supports single request ranges.
 *
 * Does not support directories or "welcome files".
 * Does not support gzip.
 * Does not support multiple request ranges.
 * Does not cache.
 *
 * POST returns 405.
 * Directories return 403.
 * Jar resources are sent with a long cache directive.
 *
 * ------------------------------------------------------------ 
 *
 * The default servlet.                                                 
 * This servlet, normally mapped to /, provides the handling for static 
 * content, OPTION and TRACE methods for the context.                   
 * The following initParameters are supported, these can be set
 * on the servlet itself:
 * <PRE>                                                                      
 *
 *  resourceBase      Set to replace the context resource base

 * </PRE>
 *                                                                    
 *
 * @author Greg Wilkins (gregw)
 * @author Nigel Canonizado
 *                                                                    
 * @since Jetty 7
 */
class BasicServlet extends HttpServlet
{   
    private static final long serialVersionUID = 1L;
    protected transient final I2PAppContext _context;
    protected transient final Log _log;
    protected File _resourceBase;
    
    private transient final MimeTypes _mimeTypes;
    
    /** same as PeerState.PARTSIZE */
    private static final int BUFSIZE = 16*1024;
    private transient ByteCache _cache = ByteCache.getInstance(16, BUFSIZE);

    private static final int FILE_CACHE_CONTROL_SECS = 24*60*60;

    public BasicServlet() {
        super();
        _context = I2PAppContext.getGlobalContext();
        _log = _context.logManager().getLog(getClass());
        _mimeTypes = new MimeTypes();
    }
    
    /* ------------------------------------------------------------ */
    public void init(ServletConfig cfg) throws ServletException {
        super.init(cfg);
        String rb=getInitParameter("resourceBase");
        if (rb!=null)
        {
            File f = new SecureFile(rb);
            setResourceBase(f);
        }
    }

    /**
     *  Files are served from here
     */
    protected synchronized void setResourceBase(File base) throws UnavailableException {
        if (!base.isDirectory()) {
            _log.error("Configured directory " + base + " does not exist");
            //throw new UnavailableException("Resource base does not exist: " + base);
        }
        _resourceBase = base;
        if (_log.shouldLog(Log.INFO))
            _log.info("Resource base is " + _resourceBase);
    }

    /** get Resource to serve.
     * Map a path to a resource. The default implementation calls
     * HttpContext.getResource but derived servlets may provide
     * their own mapping.
     * @param pathInContext The path to find a resource for.
     * @return The resource to serve or null if not existing
     */
    public File getResource(String pathInContext)
    {
        File r = null;
        if (!pathInContext.contains("..") &&
                   !pathInContext.endsWith("/")) {
            File f;
            synchronized (this) {
                if (_resourceBase==null)
                    return null;
                f = new File(_resourceBase, pathInContext);
            }
            if (f.exists())
                r = f;
        }
        return r;
    }

    /** get Resource to serve.
     * Map a path to a resource. The default implementation calls
     * HttpContext.getResource but derived servlets may provide
     * their own mapping.
     * @param pathInContext The path to find a resource for.
     * @return The resource to serve or null. Returns null for directories
     */
    public HttpContent getContent(String pathInContext)
    {
        HttpContent r = null;
        File f = getResource(pathInContext);
        // exists && !directory
        if (f != null && f.isFile())
            r = new FileContent(f);
        return r;
    }
    
    /* ------------------------------------------------------------ */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    	throws ServletException, IOException
    {
        // always starts with a '/'
        String servletpath = request.getServletPath();
        String pathInfo=request.getPathInfo();
        // ??? right??
        String pathInContext = addPaths(servletpath, pathInfo);        

        // Find the resource and content
        try {
            HttpContent content = getContent(pathInContext);
            
            // Handle resource
            if (content == null) {
                if (_log.shouldLog(Log.WARN))
                    _log.warn("Not found: " + pathInContext);
                response.sendError(404);
            } else {
                if (passConditionalHeaders(request, response, content)) {
                    if (_log.shouldLog(Log.INFO))
                        _log.info("Sending: " + content);
                    sendData(request, response, content);  
                } else {
                    if (_log.shouldLog(Log.INFO))
                        _log.info("Not modified: " + content);
                }
            }
        }
        catch(IllegalArgumentException e)
        {
            if (_log.shouldLog(Log.WARN))
                _log.warn("Error sending " + pathInContext, e);
            if(!response.isCommitted())
                response.sendError(500, e.getMessage());
        }
        catch(IOException e)
        {
            if (_log.shouldLog(Log.WARN))
                // typical browser abort
                //_log.warn("Error sending", e);
                _log.warn("Error sending " + pathInContext + ": " + e);
            throw e;
        }
    }
    
    /* ------------------------------------------------------------ */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        response.sendError(405);
    }
    
    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doTrace(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doTrace(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        response.sendError(405);
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        response.sendError(405);
    }
    
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        response.sendError(405);
    }
    

    /* ------------------------------------------------------------ */
    /** Check modification date headers.
     *  @return true to keep going, false if handled here
     */
    protected boolean passConditionalHeaders(HttpServletRequest request,HttpServletResponse response, HttpContent content)
    throws IOException
    {
        try
        {
            if (!request.getMethod().equals("HEAD") ) {
                long ifmsl=request.getDateHeader("If-Modified-Since");
                if (ifmsl!=-1)
                {
                    if (content.getLastModified()/1000 <= ifmsl/1000)
                    {
                        try {
                            response.reset();
                        } catch (IllegalStateException ise) {
                            // committed
                            return true;
                        }
                        response.setStatus(304);
                        response.getOutputStream().close();
                        return false;
                    }
                }
            }
        }
        catch(IllegalArgumentException iae)
        {
            if(!response.isCommitted())
                response.sendError(400, iae.getMessage());
            throw iae;
        }
        return true;
    }
    
    
    /* ------------------------------------------------------------ */
    protected void sendData(HttpServletRequest request,
                            HttpServletResponse response,
                            HttpContent content)
    throws IOException
    {
        InputStream in =null;
        try {
            in = content.getInputStream();
        } catch (IOException e) {
            if (_log.shouldLog(Log.WARN))
                _log.warn("Not found: " + content);
            response.sendError(404);
            return;
        }

        OutputStream out =null;
        try {
            out = response.getOutputStream();
        } catch (IllegalStateException e) {
            out = new WriterOutputStream(response.getWriter());
        }

        long content_length = content.getContentLength();

        // see if there are any range headers
        Enumeration<?> reqRanges = request.getHeaders("Range");

        if (reqRanges == null || !reqRanges.hasMoreElements()) {
            // if there were no ranges, send entire entity
            // Write content normally
            writeHeaders(response,content,content_length);
            if (content_length >= 0  && request.getMethod().equals("HEAD")) {
                // if we know the content length, don't send it to be counted
                if (_log.shouldLog(Log.INFO))
                    _log.info("HEAD: " + content);
            } else {
                // GET or unknown size for HEAD
                copy(in, out);
            }
            return;
        }


        // Parse the satisfiable ranges
        List<InclusiveByteRange> ranges = InclusiveByteRange.satisfiableRanges(reqRanges, content_length);

        // if there are no satisfiable ranges, send 416 response
        // Completely punt on multiple ranges (unlike Default)
        if (ranges == null || ranges.size() != 1) {
            writeHeaders(response, content, content_length);
            response.setStatus(416);
            response.setHeader("Content-Range", InclusiveByteRange.to416HeaderRangeString(content_length));
            in.close();
            return;
        }

        // if there is only a single valid range (must be satisfiable
        // since were here now), send that range with a 216 response
        InclusiveByteRange singleSatisfiableRange = ranges.get(0);
        long singleLength = singleSatisfiableRange.getSize(content_length);
        writeHeaders(response, content, singleLength);
        response.setStatus(206);
        response.setHeader("Content-Range", singleSatisfiableRange.toHeaderRangeString(content_length));
        copy(in, singleSatisfiableRange.getFirst(content_length), out, singleLength);
    }
    
    /* ------------------------------------------------------------ */
    protected void writeHeaders(HttpServletResponse response,HttpContent content,long count)
        throws IOException
    {   
        String rtype = response.getContentType();
        String ctype = content.getContentType();
        if (rtype != null) {
            if (rtype.equals("application/javascript"))
                response.setCharacterEncoding("ISO-8859-1");
        } else if (ctype != null) {
            response.setContentType(ctype);
            if (ctype.equals("application/javascript"))
                response.setCharacterEncoding("ISO-8859-1");
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        long lml = content.getLastModified();
        if (lml > 0)
            response.setDateHeader("Last-Modified",lml);

        if (count != -1) {
            if (count <= Integer.MAX_VALUE)
                response.setContentLength((int)count);
            else 
                response.setHeader("Content-Length", Long.toString(count));
            response.setHeader("Accept-Ranges", "bytes");
        } else {
            response.setHeader("Accept-Ranges", "none");
        }

        // add name header for muwire since the URL is just the hash
        String name = content.getContentName();
        String name2 = FilenameUtil.sanitizeFilename(name);
        String name3 = FilenameUtil.encodeFilenameRFC5987(name);
        response.addHeader("Content-Disposition", "inline; filename=\"" + name2 + "\"; " +
                           "filename*=" + name3);

        long ct = content.getCacheTime();
        if (ct>=0)
            response.setHeader("Cache-Control", "public, max-age=" + ct);
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* I2P additions below here */

    /** from Jetty HttpContent.java */
    public interface HttpContent
    {
        String getContentType();
        String getContentName();
        long getLastModified();
        /** in seconds */
        int getCacheTime();
        long getContentLength();
        InputStream getInputStream() throws IOException;
    }

    private class FileContent implements HttpContent
    {
        private final File _file;
        
        public FileContent(File file)
        {
            _file = file;
        }
        
        /* ------------------------------------------------------------ */
        public String getContentType()
        {
            //return _mimeTypes.getMimeByExtension(_file.toString());
            return getMimeType(_file.toString());
        }

        public String getContentName()
        {
            return _file.getName();
        }

        /* ------------------------------------------------------------ */
        public long getLastModified()
        {
            return _file.lastModified();
        }

        public int getCacheTime()
        {
            return FILE_CACHE_CONTROL_SECS;
        }

        /* ------------------------------------------------------------ */
        public long getContentLength()
        {
            return _file.length();
        }

        /* ------------------------------------------------------------ */
        public InputStream getInputStream() throws IOException
        {
            return new BufferedInputStream(new FileInputStream(_file));
        }

        @Override
        public String toString() { return "File \"" + _file + '"'; }
    }

    /**
     * @param resourcePath in the classpath, without ".properties" extension
     */
    protected void loadMimeMap(String resourcePath) {
        _mimeTypes.loadMimeMap(resourcePath);
    }

    /* ------------------------------------------------------------ */
    /** Get the MIME type by filename extension.
     * @param filename A file name
     * @return MIME type matching the longest dot extension of the
     * file name.
     */
    protected String getMimeType(String filename) {
        String rv = _mimeTypes.getMimeByExtension(filename);
        if (rv != null)
            return rv;
        return getServletContext().getMimeType(filename);
    }

    protected void addMimeMapping(String extension, String type) {
        _mimeTypes.addMimeMapping(extension, type);
    }

    /**
     *  Simple version of URIUtil.addPaths()
     *  @param path may be null
     */
    protected static String addPaths(String base, String path) {
        if (path == null)
            return base;
        String rv = (new File(base, path)).toString();
        if (SystemVersion.isWindows())
            rv = rv.replace("\\", "/");
        return rv;
    }

    /**
     *  Write from in to out
     */
    private void copy(InputStream in, OutputStream out) throws IOException {
        copy(in, 0, out, -1);
    }

    /**
     *  Write from in to out
     */
    private void copy(InputStream in, long skip, OutputStream out, final long len) throws IOException {
        ByteArray ba = _cache.acquire();
        byte[] buf = ba.getData();
        try {
            if (skip > 0)
                DataHelper.skip(in, skip);
            int read = 0;
            long tot = 0;
            boolean done = false;
            while ( (read = in.read(buf)) != -1 && !done) {
                if (len >= 0) {
                    tot += read;
                    if (tot >= len) {
                        read -= (int) (tot - len);
                        done = true;
                    }
                }
                out.write(buf, 0, read);
            }
        } finally {
            _cache.release(ba, false);
            if (in != null) 
                try { in.close(); } catch (IOException ioe) {}
            if (out != null) 
                try { out.close(); } catch (IOException ioe) {}
        }
    }
}
