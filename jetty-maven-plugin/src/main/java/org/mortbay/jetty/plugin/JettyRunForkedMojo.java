// ========================================================================
// Copyright (c) Webtide LLC
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.mortbay.jetty.plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.resource.Resource;


/**
 * <p>
 *  This goal is used to assemble your webapp into a war and automatically deploy it to Jetty in a forked JVM.
 *  </p>
 *  <p>
 *  You need to define a jetty.xml file to configure connectors etc and a context xml file that sets up anything special
 *  about your webapp. This plugin will fill in the:
 *  <ul>
 *  <li>context path
 *  <li>classes
 *  <li>web.xml
 *  <li>root of the webapp
 *  </ul>
 *  Based on a combination of information that you supply and the location of files in your unassembled webapp.
 *  </p>
 *  <p>
 *  There is a <a href="run-war-mojo.html">reference guide</a> to the configuration parameters for this plugin, and more detailed information
 *  with examples in the <a href="http://docs.codehaus.org/display/JETTY/Maven+Jetty+Plugin/">Configuration Guide</a>.
 *  </p>
 * 
 * @goal run-forked
 * @requiresDependencyResolution compile+runtime
 * @execute phase="test-compile"
 * @description Runs Jetty in forked JVM on an unassembled webapp
 *
 */
public class JettyRunForkedMojo extends AbstractMojo
{    
    public String PORT_SYSPROPERTY = "jetty.port";
    
    /**
     * Whether or not to include dependencies on the plugin's classpath with &lt;scope&gt;provided&lt;/scope&gt;
     * Use WITH CAUTION as you may wind up with duplicate jars/classes.
     * @parameter alias="useProvidedScope" default-value="false"
     */
    protected boolean useProvided;
    
    
    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    

    
    /**
     * If true, the &lt;testOutputDirectory&gt;
     * and the dependencies of &lt;scope&gt;test&lt;scope&gt;
     * will be put first on the runtime classpath.
     * @parameter default-value="false"
     */
    private boolean useTestClasspath;
    
    
    /**
     * The default location of the web.xml file. Will be used
     * if <webAppConfig><descriptor> is not set.
     * 
     * @parameter expression="${basedir}/src/main/webapp/WEB-INF/web.xml"
     * @readonly
     */
    private String webXml;
    
    /**
     * The target directory
     * 
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    protected File target;
    
    
    /**
     * The temporary directory to use for the webapp.
     * Defaults to target/tmp
     *
     * @parameter expression="${project.build.directory}/tmp"
     * @required
     * @readonly
     */
    protected File tmpDirectory;

    
    /**
     * The directory containing generated classes.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     * 
     */
    private File classesDirectory;
    
    
    
    /**
     * The directory containing generated test classes.
     * 
     * @parameter expression="${project.build.testOutputDirectory}"
     * @required
     */
    private File testClassesDirectory;
    
    /**
     * Root directory for all html/jsp etc files
     *
     * @parameter expression="${basedir}/src/main/webapp"
     *
     */
    private File webAppSourceDirectory;
    

    /**
     * Location of jetty xml configuration files whose contents 
     * will be applied before any plugin configuration. Optional.
     * @parameter
     */
    private String jettyXml;
    
    /**
     * The context path for the webapp. Defaults to the
     * name of the webapp's artifact.
     *
     * @parameter expression="/${project.artifactId}"
     * @required
     * @readonly
     */
    private String contextPath;


    /**
     * Location of a context xml configuration file whose contents
     * will be applied to the webapp AFTER anything in &lt;webAppConfig&gt;.Optional.
     * @parameter
     */
    private String contextXml;

    
    /**  
     * @parameter expression="${jetty.skip}" default-value="false"
     */
    private boolean skip;

    /**
     * Port to listen to stop jetty on executing -DSTOP.PORT=&lt;stopPort&gt; 
     * -DSTOP.KEY=&lt;stopKey&gt; -jar start.jar --stop
     * @parameter
     * @required
     */
    protected int stopPort;
    
    /**
     * Key to provide when stopping jetty on executing java -DSTOP.KEY=&lt;stopKey&gt; 
     * -DSTOP.PORT=&lt;stopPort&gt; -jar start.jar --stop
     * @parameter
     * @required
     */
    protected String stopKey;

    /**
     * @parameter expression="${plugin.artifacts}"
     * @readonly
     */
    private List pluginArtifacts;
    
    
    /**
     * @parameter expression="${plugin}"
     * @readonly
     */
    private PluginDescriptor plugin;
    
    
    /** @component */
    //private org.apache.maven.artifact.resolver.ArtifactResolver resolver;
     
    /**@parameter default-value="${localRepository}" */
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;
     
    /** @parameter default-value="${project.remoteArtifactRepositories}" */
    private java.util.List remoteRepositories;
    
    
    
    private Process forkedProcess;
    
    
    public class ShutdownThread extends Thread
    {
        public ShutdownThread()
        {
            super("RunForkedShutdown");
        }
        
        public void run ()
        {
            if (forkedProcess != null)
            {
                forkedProcess.destroy();
            }
        }
    }
    
    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        getLog().info("Configuring Jetty for project: " + project.getName());
        if (skip)
        {
            getLog().info("Skipping Jetty start: jetty.skip==true");
            return;
        }
        PluginLog.setLog(getLog());
        Runtime.getRuntime().addShutdownHook(new ShutdownThread());
        startJettyRunner();
    }
    
    
    public List<String> getProvidedJars() throws MojoExecutionException
    {  
        //if we are configured to include the provided dependencies on the plugin's classpath
        //(which mimics being on jetty's classpath vs being on the webapp's classpath), we first
        //try and filter out ones that will clash with jars that are plugin dependencies, then
        //create a new classloader that we setup in the parent chain.
        if (useProvided)
        {
            
                List<String> provided = new ArrayList<String>();        
                for ( Iterator<Artifact> iter = project.getArtifacts().iterator(); iter.hasNext(); )
                {                   
                    Artifact artifact = iter.next();
                    if (Artifact.SCOPE_PROVIDED.equals(artifact.getScope()) && !isPluginArtifact(artifact))
                    {
                        provided.add(artifact.getFile().getAbsolutePath());
                        if (getLog().isDebugEnabled()) { getLog().debug("Adding provided artifact: "+artifact);}
                    }
                }
                return provided;

        }
        else
            return null;
    }
    
    /* ------------------------------------------------------------ */
    public File prepareConfiguration() throws MojoExecutionException
    {
        try
        {   
            //work out the configuration based on what is configured in the pom
            File propsFile = new File (target, "fork.props");
            if (propsFile.exists())
                propsFile.delete();   

            propsFile.createNewFile();
            //propsFile.deleteOnExit();

            Properties props = new Properties();


            //web.xml
            if (webXml != null)
                props.put("web.xml", webXml);

            //sort out the context path
            if (contextPath != null)
                props.put("context.path", contextPath);

            //sort out the tmp directory (make it if it doesn't exist)
            if (tmpDirectory != null)
            {
                if (!tmpDirectory.exists())
                    tmpDirectory.mkdirs();
                props.put("tmp.dir", tmpDirectory.getAbsolutePath());
            }

            //sort out the base directory of the webapp
            if (webAppSourceDirectory != null)
                props.put("base.dir", webAppSourceDirectory.getAbsolutePath());

            //web-inf classes
            List<File> classDirs = getClassesDirs();
            StringBuffer strbuff = new StringBuffer();
            for (int i=0; i<classDirs.size(); i++)
            {
                File f = classDirs.get(i);
                strbuff.append(f.getAbsolutePath());
                if (i < classDirs.size()-1)
                    strbuff.append(",");
            }

            if (classesDirectory != null)
            {
                props.put("classes.dir", classesDirectory.getAbsolutePath());
            }
            
            if (useTestClasspath && testClassesDirectory != null)
            {
                props.put("testClasses.dir", testClassesDirectory.getAbsolutePath());
            }

            //web-inf lib
            List<File> deps = getDependencyFiles();
            strbuff.setLength(0);
            for (int i=0; i<deps.size(); i++)
            {
                File d = deps.get(i);
                strbuff.append(d.getAbsolutePath());
                if (i < deps.size()-1)
                    strbuff.append(",");
            }
            props.put("lib.jars", strbuff.toString());

            //any overlays
            List<Resource> overlays = getOverlays();
            strbuff.setLength(0);
            for (int i=0; i<overlays.size(); i++)
            {
                Resource r = overlays.get(i);
                strbuff.append(r.getFile().getAbsolutePath());
                if (i < overlays.size()-1)
                    strbuff.append(",");
            }
            props.put("overlay.files", strbuff.toString());

            props.store(new BufferedWriter(new FileWriter(propsFile)), "properties for forked webapp");
            return propsFile;
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Prepare webapp configuration", e);
        }
    }

    private List<File> getClassesDirs ()
    {
        List<File> classesDirs = new ArrayList<File>();
        
        //if using the test classes, make sure they are first
        //on the list
        if (useTestClasspath && (testClassesDirectory != null))
            classesDirs.add(testClassesDirectory);
        
        if (classesDirectory != null)
            classesDirs.add(classesDirectory);
        
        return classesDirs;
    }
  
    
    
    private List<Resource> getOverlays()
    {
        List<Resource> overlays = new ArrayList<Resource>();
        for ( Iterator<Artifact> iter = project.getArtifacts().iterator(); iter.hasNext(); )
        {
            Artifact artifact = (Artifact) iter.next();  
            // Include runtime and compile time libraries, and possibly test libs too
            if(artifact.getType().equals("war"))
            {
                try
                {
                    Resource r=Resource.newResource("jar:"+Resource.toURL(artifact.getFile()).toString()+"!/");
                    overlays.add(r);
                }
                catch(Exception e)
                {
                    throw new RuntimeException(e);
                }
                continue;
            }
        }
        
        return overlays;
    }
    
    
    
    private List<File> getDependencyFiles ()
    {
        List<File> dependencyFiles = new ArrayList<File>();
    
        for ( Iterator<Artifact> iter = project.getArtifacts().iterator(); iter.hasNext(); )
        {
            Artifact artifact = (Artifact) iter.next();
            
            if (((!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) && (!Artifact.SCOPE_TEST.equals( artifact.getScope()))) 
                    ||
                (useTestClasspath && Artifact.SCOPE_TEST.equals( artifact.getScope())))
            {
                dependencyFiles.add(artifact.getFile());
                getLog().debug( "Adding artifact " + artifact.getFile().getName() + " for WEB-INF/lib " );   
            }
        }
        
        return dependencyFiles; 
    }
    
    public boolean isPluginArtifact(Artifact artifact)
    {
        if (pluginArtifacts == null || pluginArtifacts.isEmpty())
            return false;
        
        boolean isPluginArtifact = false;
        for (Iterator<Artifact> iter = pluginArtifacts.iterator(); iter.hasNext() && !isPluginArtifact; )
        {
            Artifact pluginArtifact = iter.next();
            if (getLog().isDebugEnabled()) { getLog().debug("Checking "+pluginArtifact);}
            if (pluginArtifact.getGroupId().equals(artifact.getGroupId()) && pluginArtifact.getArtifactId().equals(artifact.getArtifactId()))
                isPluginArtifact = true;
        }
        
        return isPluginArtifact;
    }
    
    
    
    private Set<Artifact>  getExtraJars()
    throws Exception
    {
        Set<Artifact> extraJars = new HashSet<Artifact>();
  
        
        List l = pluginArtifacts;
        Artifact pluginArtifact = null;

        if (l != null)
        {
            Iterator itor = l.iterator();
            while (itor.hasNext() && pluginArtifact == null)
            {              
                Artifact a = (Artifact)itor.next();
                if (a.getArtifactId().equals(plugin.getArtifactId())) //get the jetty-maven-plugin jar
                {
                    extraJars.add(a);
                }
            }
        }

        return extraJars;
    }

    
    /* ------------------------------------------------------------ */
    public void startJettyRunner() throws MojoExecutionException
    {      
        try
        {
        
            File props = prepareConfiguration();
            
            List<String> cmd = new ArrayList<String>();
            cmd.add(getJavaBin());
            
            String classPath = getClassPath();
            if (classPath != null && classPath.length() > 0)
            {
                cmd.add("-cp");
                cmd.add(classPath);
            }
            cmd.add(Starter.class.getCanonicalName());
            
            if (stopPort > 0 && stopKey != null)
            {
                cmd.add("--stop-port");
                cmd.add(Integer.toString(stopPort));
                cmd.add("--stop-key");
                cmd.add(stopKey);
            }
            if (jettyXml != null)
            {
                cmd.add("--jetty-xml");
                cmd.add(jettyXml);
            }
        
            if (contextXml != null)
            {
                cmd.add("--context-xml");
                cmd.add(contextXml);
            }
            
            cmd.add("--props");
            cmd.add(props.getAbsolutePath());
            
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.directory(project.getBasedir());
            
            if (PluginLog.getLog().isDebugEnabled())
                PluginLog.getLog().debug(Arrays.toString(cmd.toArray()));
            
            forkedProcess = builder.start();

            startPump("STDOUT",forkedProcess.getInputStream());
            startPump("STDERR",forkedProcess.getErrorStream());
            
            int exitcode = forkedProcess.waitFor();
            
            PluginLog.getLog().info("Forked execution exit: "+exitcode);
        }
        catch (InterruptedException ex)
        {
            if (forkedProcess != null)
                forkedProcess.destroy();
            
            throw new MojoExecutionException("Failed to start Jetty within time limit");
        }
        catch (Exception ex)
        {
            if (forkedProcess != null)
                forkedProcess.destroy();
            
            throw new MojoExecutionException("Failed to create Jetty process", ex);
        }
    }
    
 
    
    public String getClassPath() throws Exception
    {
        StringBuilder classPath = new StringBuilder();
        for (Object obj : pluginArtifacts)
        {
            Artifact artifact = (Artifact) obj;
            if ("jar".equals(artifact.getType()))
            {
                if (classPath.length() > 0)
                {
                    classPath.append(':');
                }
                classPath.append(artifact.getFile().getAbsolutePath());

            }
        }
        
        //Any jars that we need from the plugin environment (like the ones containing Starter class)
        Set<Artifact> extraJars = getExtraJars();
        for (Artifact a:extraJars)
        { 
            classPath.append(':');
            classPath.append(a.getFile().getAbsolutePath());
        }
        
        
        //Any jars that we need from the project's dependencies because we're useProvided
        List<String> providedJars = getProvidedJars();
        if (providedJars != null && !providedJars.isEmpty())
        {
            for (String jar:providedJars)
            {
                classPath.append(':');
                classPath.append(jar);
                if (getLog().isDebugEnabled()) getLog().debug("Adding provided jar: "+jar);
            }
        }
        
        return pathSeparators(classPath.toString());
    }

    private String getJavaBin()
    {
        String javaexes[] = new String[]
        { "java", "java.exe" };

        File javaHomeDir = new File(System.getProperty("java.home"));
        for (String javaexe : javaexes)
        {
            File javabin = new File(javaHomeDir,fileSeparators("bin/" + javaexe));
            if (javabin.exists() && javabin.isFile())
            {
                return javabin.getAbsolutePath();
            }
        }

        return "java";
    }
    
    public static String fileSeparators(String path)
    {
        StringBuilder ret = new StringBuilder();
        for (char c : path.toCharArray())
        {
            if ((c == '/') || (c == '\\'))
            {
                ret.append(File.separatorChar);
            }
            else
            {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    public static String pathSeparators(String path)
    {
        StringBuilder ret = new StringBuilder();
        for (char c : path.toCharArray())
        {
            if ((c == ',') || (c == ':'))
            {
                ret.append(File.pathSeparatorChar);
            }
            else
            {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    private void startPump(String mode, InputStream inputStream)
    {
        ConsoleStreamer pump = new ConsoleStreamer(mode,inputStream);
        Thread thread = new Thread(pump,"ConsoleStreamer/" + mode);
        thread.start();
    }

  


    /**
     * Simple streamer for the console output from a Process
     */
    private static class ConsoleStreamer implements Runnable
    {
        private String mode;
        private BufferedReader reader;

        public ConsoleStreamer(String mode, InputStream is)
        {
            this.mode = mode;
            this.reader = new BufferedReader(new InputStreamReader(is));
        }


        public void run()
        {
            String line;
            try
            {
                while ((line = reader.readLine()) != (null))
                {
                    System.out.println("[" + mode + "] " + line);
                }
            }
            catch (IOException ignore)
            {
                /* ignore */
            }
            finally
            {
                IO.close(reader);
            }
        }
    }
}
