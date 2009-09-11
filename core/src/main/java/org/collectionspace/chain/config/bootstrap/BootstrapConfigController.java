/* Copyright 2009 University of Cambridge
 * Licensed under the Educational Community License (ECL), Version 2.0. You may not use this file except in 
 * compliance with this License.
 *
 * You may obtain a copy of the ECL 2.0 License at https://source.collectionspace.org/collection-space/LICENSE.txt
 */
package org.collectionspace.chain.config.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.apache.commons.lang.StringUtils;
import org.collectionspace.chain.config.api.ConfigLoadFailedException;
import org.collectionspace.kludge.BCCKludge;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

/** the main controller/entry-point to the bootstrap config
 * 
 */
public class BootstrapConfigController implements BCCKludge {		
	private ServletContext ctx;
	private List<String> suffixes=new ArrayList<String>();
	private Map<String,ConfigLoadMethod> methods=new HashMap<String,ConfigLoadMethod>();
	private Map<String,List<ConfigOptionSource>> sources=new HashMap<String,List<ConfigOptionSource>>();
	
	private InputStream tryPath(String filename) {
		System.err.println(filename);
		String config_file=getClass().getPackage().getName().replaceAll("\\.","/")+"/"+filename;
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(config_file);
	}
	
	private Document loadConfigLoader() throws ConfigLoadFailedException, DocumentException, IOException {
		InputStream config=null;
		for(String suffix : suffixes) {
			config=tryPath(suffix);
			if(config!=null)
				break;
		}
		if(config==null)
			throw new ConfigLoadFailedException("Cannot find config-loader.xml");
		SAXReader reader=new SAXReader();
		Document out=reader.read(config);
		config.close();
		return out;
	}
	
	/* Used for testing */
	public void addSearchSuffix(String extra) {
		suffixes.add(0,extra);
	}

	public BootstrapConfigController(ServletContext ctx) {
		this.ctx=ctx;
		suffixes.add("config-loader.xml");
	}
		
	private void loadMethod(Document root,String name,ConfigLoadMethod method) throws ConfigLoadFailedException {
		methods.put(name,method);
		method.init(this,root);
	}
	
	private void loadMethods(Document root) throws ConfigLoadFailedException {
		loadMethod(root,"default",new DefaultConfigLoadMethod());
		loadMethod(root,"attribute",new AttributeConfigLoadMethod());
		loadMethod(root,"property",new PropertyConfigLoadMethod());		
		loadMethod(root,"tmpdir",new TmpdirConfigLoadMethod());
		loadMethod(root,"services",new ServicesRespondingConfigLoadMethod());
		loadMethod(root,"classpath",new ClasspathConfigLoadMethod());
	}
	
	private List<ConfigOptionSource> loadSources(Element el) throws ConfigLoadFailedException {
		List<ConfigOptionSource> out=new ArrayList<ConfigOptionSource>();
		for(Object tag : el.selectNodes("*")) {
			if(!(tag instanceof Element))
				continue; // Should be impossible
			String name=((Element)tag).getName();
			ConfigLoadMethod method=methods.get(name);
			if(method==null)
				throw new ConfigLoadFailedException("No such method "+name);
			out.add(new ConfigOptionSource(method,(Element)tag));
		}
		return out;
	}
	
	public void go() throws ConfigLoadFailedException {
		try {
			Document config_loader=loadConfigLoader();
			loadMethods(config_loader);
			for(Object el : config_loader.selectNodes("config/option")) {
				if(!(el instanceof Element))
					continue; // Should be impossible
				String name=((Element)el).attributeValue("name");
				if(StringUtils.isBlank(name))
					throw new ConfigLoadFailedException("option tag needs name attribute");
				sources.put(name.trim(),loadSources((Element)el));
			}			
		} catch(ConfigLoadFailedException e) {
			throw e; // Don't wrap
		} catch(Exception e) {
			throw new ConfigLoadFailedException("Nested exception loading config",e);
		}
	}
	
	public String getOption(String key) {
		List<ConfigOptionSource> source_list=sources.get(key);
		if(source_list==null)
			return null;
		for(ConfigOptionSource source : source_list) {
			String out=source.getString();
			if(out!=null)
				return out;
		}
		return null;
	}
	
	protected ServletContext getServletContext() { return ctx; }
}
