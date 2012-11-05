//========================================================================
//$Id$
//Copyright 2006 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.mortbay.jetty.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;

/**
 * JettyWebAppContext
 * 
 * Extends the WebAppContext to specialize for the maven environment.
 * We pass in the list of files that should form the classpath for
 * the webapp when executing in the plugin, and any jetty-env.xml file
 * that may have been configured.
 *
 */
public class JettyWebAppContext extends WebAppContext
{
    private static final Logger LOG = Log.getLogger(JettyWebAppContext.class);

    private static final String DEFAULT_CONTAINER_INCLUDE_JAR_PATTERN = ".*/javax.servlet-[^/]*\\.jar$|.*/servlet-api-[^/]*\\.jar$";
    private static final String WEB_INF_CLASSES_PREFIX = "/WEB-INF/classes";
    private static final String WEB_INF_LIB_PREFIX = "/WEB-INF/lib";

    private File classes = null;
    private File testClasses = null;
    private final List<File> webInfClasses = new ArrayList<File>();
    private final List<File> webInfJars = new ArrayList<File>();
    private final Map<String, File> webInfJarMap = new HashMap<String, File>();
    private final EnvConfiguration envConfig;
    private List<File> classpathFiles;  //webInfClasses+testClasses+webInfJars
    private String jettyEnvXml;
    private List<Resource> overlays;
    
    /**
     * @deprecated The value of this parameter will be ignored by the plugin. Overlays will always be unpacked.
     */
    private boolean unpackOverlays;
    
    /**
     * Set the "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern" with a pattern for matching jars on
     * container classpath to scan. This is analogous to the WebAppContext.setAttribute() call.
     */
    private String containerIncludeJarPattern = null;
    
    /**
     * Set the "org.eclipse.jetty.server.webapp.WebInfIncludeJarPattern" with a pattern for matching jars on
     * webapp's classpath to scan. This is analogous to the WebAppContext.setAttribute() call.
     */
    private String webInfIncludeJarPattern = null;
    

    /**
     * @deprecated The value of this parameter will be ignored by the plugin. This option will be always disabled. 
     */
    private boolean copyWebInf;

    private boolean baseAppFirst = true;

    public JettyWebAppContext ()
    throws Exception
    {
        super();   
        setConfigurations(new Configuration[]{
                new MavenWebInfConfiguration(),
                new WebXmlConfiguration(),
                new MetaInfConfiguration(),
                new FragmentConfiguration(),
                envConfig = new EnvConfiguration(),
                new org.eclipse.jetty.plus.webapp.PlusConfiguration(),
                new MavenAnnotationConfiguration(),
                new JettyWebXmlConfiguration()
        });
        // Turn off copyWebInf option as it is not applicable for plugin.
        super.setCopyWebInf(false);
    }
    public void setContainerIncludeJarPattern(String pattern)
    {
    	containerIncludeJarPattern = pattern;
    }
    
    public String getContainerIncludeJarPattern()
    {
        return containerIncludeJarPattern;
    }
    
    
    public String getWebInfIncludeJarPattern()
    {
    	return webInfIncludeJarPattern;
    }
    public void setWebInfIncludeJarPattern(String pattern)
    {
        webInfIncludeJarPattern = pattern;
    }
   
    
    
    public boolean getUnpackOverlays()
    {
        return unpackOverlays;
    }

    public void setUnpackOverlays(boolean unpackOverlays)
    {
        this.unpackOverlays = unpackOverlays;
    }
   
    public List<File> getClassPathFiles()
    {
        return this.classpathFiles;
    }
    
    public void setOverlays (List<Resource> overlays)
    {
        this.overlays = overlays;
    }
    
    public List<Resource> getOverlays ()
    {
        return this.overlays;
    }
    
    public void setJettyEnvXml (String jettyEnvXml)
    {
        this.jettyEnvXml = jettyEnvXml;
    }
    
    public String getJettyEnvXml()
    {
        return this.jettyEnvXml;
    }

   
    public void setClasses(File dir)
    {
        classes = dir;
    }
    
    public File getClasses()
    {
        return classes;
    }
    
    public void setWebInfLib (List<File> jars)
    {
        webInfJars.addAll(jars);
    }
    
    
    public void setTestClasses (File dir)
    {
        testClasses = dir;
    }
    
    
    public File getTestClasses ()
    {
        return testClasses;
    }
    
    
    /* ------------------------------------------------------------ */
    @Override
    public void setCopyWebInf(boolean value)
    {
        copyWebInf = value;
    }

    /* ------------------------------------------------------------ */
    @Override
    public boolean isCopyWebInf()
    {
        return copyWebInf;
    }

    /* ------------------------------------------------------------ */
    public void setBaseAppFirst(boolean value)
    {
        baseAppFirst = value;
    }

    /* ------------------------------------------------------------ */
    public boolean getBaseAppFirst()
    {
        return baseAppFirst;
    }

    /* ------------------------------------------------------------ */
    /**
     * This method is provided as a convenience for jetty maven plugin configuration 
     * @param resourceBases Array of resources strings to set as a {@link ResourceCollection}. Each resource string may be a comma separated list of resources
     * @see Resource
     */
    public void setResourceBases(String[] resourceBases)
    {
        List<String> resources = new ArrayList<String>();
        for (String rl:resourceBases)
        {
            String[] rs = rl.split(" *, *");
            for (String r:rs)
                resources.add(r);
        }
        
        setBaseResource(new ResourceCollection(resources.toArray(new String[resources.size()])));
    }

    public List<File> getWebInfLib()
    {
        return webInfJars;
    }

    public void doStart () throws Exception
    {
        //Set up the pattern that tells us where the jars are that need scanning for
        //stuff like taglibs so we can tell jasper about it (see TagLibConfiguration)

        //Allow user to set up pattern for names of jars from the container classpath
        //that will be scanned - note that by default NO jars are scanned
        String tmp = containerIncludeJarPattern;
        if (tmp==null || "".equals(tmp))
            tmp = (String)getAttribute(WebInfConfiguration.CONTAINER_JAR_PATTERN);           
        
        tmp = addPattern(tmp, DEFAULT_CONTAINER_INCLUDE_JAR_PATTERN);
        setAttribute(WebInfConfiguration.CONTAINER_JAR_PATTERN, tmp);
        
        //Allow user to set up pattern of jar names from WEB-INF that will be scanned.
        //Note that by default ALL jars considered to be in WEB-INF will be scanned - setting
        //a pattern restricts scanning
        if (webInfIncludeJarPattern != null)
            setAttribute(WebInfConfiguration.WEBINF_JAR_PATTERN, webInfIncludeJarPattern);
   
        //Set up the classes dirs that comprises the equivalent of WEB-INF/classes
        if (testClasses != null)
            webInfClasses.add(testClasses);
        if (classes != null)
            webInfClasses.add(classes);
        
        // Set up the classpath
        classpathFiles = new ArrayList<File>();
        classpathFiles.addAll(webInfClasses);
        classpathFiles.addAll(webInfJars);

        // Initialize map containing all jars in /WEB-INF/lib
        webInfJarMap.clear();
        for (File file : webInfJars)
        {
            // Return all jar files from class path
            String fileName = file.getName();
            if (fileName.endsWith(".jar"))
                webInfJarMap.put(fileName, file);
        }

        if (this.jettyEnvXml != null)
            envConfig.setJettyEnvXml(Resource.toURL(new File(this.jettyEnvXml)));
        
        setShutdown(false);
        super.doStart();
    }
     
    public void doStop () throws Exception
    { 
        if (classpathFiles != null)
            classpathFiles.clear();
        classpathFiles = null;
        
        classes = null;
        testClasses = null;
        
        if (webInfJarMap != null)
            webInfJarMap.clear();
       
        webInfClasses.clear();
        webInfJars.clear();
        
        
        
        setShutdown(true);
        //just wait a little while to ensure no requests are still being processed
        Thread.currentThread().sleep(500L);
        super.doStop();
    }

    @Override
    public Resource getResource(String uriInContext) throws MalformedURLException
    {
        Resource resource = null;
        // Try to get regular resource
        resource = super.getResource(uriInContext);

        // If no regular resource exists check for access to /WEB-INF/lib or /WEB-INF/classes
        if ((resource == null || !resource.exists()) && uriInContext != null && classes != null)
        {
            String uri = URIUtil.canonicalPath(uriInContext);
            if (uri == null)
                return null;

            try
            {
                // Replace /WEB-INF/classes with candidates for the classpath
                if (uri.startsWith(WEB_INF_CLASSES_PREFIX))
                {
                    if (uri.equalsIgnoreCase(WEB_INF_CLASSES_PREFIX) || uri.equalsIgnoreCase(WEB_INF_CLASSES_PREFIX+"/"))
                    {
                        //exact match for a WEB-INF/classes, so preferentially return the resource matching the web-inf classes
                        //rather than the test classes
                        if (classes != null)
                            return Resource.newResource(classes);
                        else if (testClasses != null)
                            return Resource.newResource(testClasses);
                    }
                    else
                    {
                        //try matching                       
                        Resource res = null;
                        int i=0;
                        while (res == null && (i < webInfClasses.size()))
                        {
                            String newPath = uri.replace(WEB_INF_CLASSES_PREFIX, webInfClasses.get(i).getPath());
                            res = Resource.newResource(newPath);
                            if (!res.exists())
                            {
                                res = null; 
                                i++;
                            }
                        }
                        return res;
                    }
                }       
                else if (uri.startsWith(WEB_INF_LIB_PREFIX))
                {
                    // Return the real jar file for all accesses to
                    // /WEB-INF/lib/*.jar
                    String jarName = uri.replace(WEB_INF_LIB_PREFIX, "");
                    if (jarName.startsWith("/") || jarName.startsWith("\\")) 
                        jarName = jarName.substring(1);
                    if (jarName.length()==0) 
                        return null;
                    File jarFile = webInfJarMap.get(jarName);
                    if (jarFile != null)
                        return Resource.newResource(jarFile.getPath());

                    return null;
                }
            }
            catch (MalformedURLException e)
            {
                throw e;
            }
            catch (IOException e)
            {
                LOG.ignore(e);
            }
        }
        return resource;
    }

    @Override
    public Set<String> getResourcePaths(String path)
    {
        // Try to get regular resource paths - this will get appropriate paths from any overlaid wars etc
        Set<String> paths = super.getResourcePaths(path);
        
        if (path != null)
        {
            TreeSet<String> allPaths = new TreeSet<String>();
            allPaths.addAll(paths);
            
            //add in the dependency jars as a virtual WEB-INF/lib entry
            if (path.startsWith(WEB_INF_LIB_PREFIX))
            {
                for (String fileName : webInfJarMap.keySet())
                {
                    // Return all jar files from class path
                    allPaths.add(WEB_INF_LIB_PREFIX + "/" + fileName);
                }
            }
            else if (path.startsWith(WEB_INF_CLASSES_PREFIX))
            {
                int i=0;
               
                while (i < webInfClasses.size())
                {
                    String newPath = path.replace(WEB_INF_CLASSES_PREFIX, webInfClasses.get(i).getPath());
                    allPaths.addAll(super.getResourcePaths(newPath));
                    i++;
                }
            }
            return allPaths;
        }
        return paths;
    }
    
    
    public String addPattern (String s, String pattern)
    {
        if (s == null)
            s = "";
        else
            s = s.trim();
        
        if (!s.contains(pattern))
        {
            if (s.length() != 0)
                s = s + "|";
            s = s + pattern;
        }
        
        return s;
    }
}
