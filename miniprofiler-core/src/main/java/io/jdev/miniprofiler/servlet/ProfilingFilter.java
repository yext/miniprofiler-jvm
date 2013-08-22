/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jdev.miniprofiler.servlet;

import io.jdev.miniprofiler.MiniProfiler;
import io.jdev.miniprofiler.Profiler;
import io.jdev.miniprofiler.ProfilerProvider;
import io.jdev.miniprofiler.json.JsonUtil;
import io.jdev.miniprofiler.util.ResourceHelper;
import io.jdev.miniprofiler.storage.Storage;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filter to start and end profiling, and serve up results as JSON.
 *
 * <p>If a profiler provider is injected using
 * {@link #setProfilerProvider(io.jdev.miniprofiler.ProfilerProvider)}, then
 * it will be used to start new profiling sessions. Otherwise the filter will
 * call {@link MiniProfiler#start(String)}, and it will be up to any setup
 * code to set the profiler provider statically using
 * {@link MiniProfiler#setProfilerProvider(io.jdev.miniprofiler.ProfilerProvider)}
 * if the default profiler provider isn't enough.</p>
 */
public class ProfilingFilter implements Filter {

	protected ProfilerProvider profilerProvider;
	// TODO: un-hardcode this
	protected String profilerPath = "/miniprofiler/";
	protected ResourceHelper resourceHelper = new ResourceHelper();
	protected ServletContext servletContext;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		servletContext = filterConfig.getServletContext();
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		// handle serving up resources
		String uri = request.getRequestURI();
		String requestBasePath = request.getContextPath() + profilerPath;
		if (resourceHelper.uriMatches(requestBasePath, uri)) {
			serveResource(request, response, uri, requestBasePath);
			return;
		}

		if(!shouldProfileRequest(request)) {
			// just pass on
			filterChain.doFilter(servletRequest, servletResponse);
		} else {
			Profiler profiler = startProfiling(request);
			try {
				// add header, this is mostly for ajax
				UUID id = profiler.getId();
				if (id != null) {
					response.addHeader("X-MiniProfiler-Ids", "[\"" + id.toString() + "\"]");
				}
					filterChain.doFilter(servletRequest, servletResponse);
			} finally {
				profiler.stop();
			}
		}
	}

	private void serveResource(HttpServletRequest request, HttpServletResponse response, String uri, String requestBasePath) throws IOException {
		if (resourceHelper.stripBasePath(requestBasePath, uri).equals("results")) {
			serveResults(request, response);
			return;
		}
		// serve up stuff
		ResourceHelper.Resource resource = resourceHelper.getResource(requestBasePath, uri);
		if (resource != null) {
			response.setContentLength(resource.getContentLength());
			response.setContentType(resource.getContentType());
			OutputStream os = response.getOutputStream();
			os.write(resource.getContent());
			os.close();
		} else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}


	private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	private void serveResults(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String id = request.getParameter("id");
		if (id == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		if (id.matches("\\[.+\\]")) {
			id = id.substring(1, id.length() - 1);
		}
		if (!UUID_PATTERN.matcher(id).matches()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		Storage storage = profilerProvider != null ? profilerProvider.getStorage() : MiniProfiler.getStorage();
		Profiler profiler = storage.load(UUID.fromString(id));
		if (profiler == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		response.setContentType("application/json");
		String json = JsonUtil.toJson(profiler);
		Writer writer = response.getWriter();
		writer.write(json);
		writer.close();
	}

	protected Profiler startProfiling(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (profilerProvider != null) {
			return profilerProvider.start(uri);
		} else {
			// use global one instead
			return MiniProfiler.start(uri);
		}
	}

	@Override
	public void destroy() {
	}

	/**
	 * Called when the filter is determining whether to profile the request or not.
	 * The default implementation won't profile requests which correspond to an actual
	 * file in the web app, except for URIs ending in .jsp or /, as those may well be
	 * dynamic anyway. Override to customise the default behaviour.
	 *
	 * @param request the request to profile (or not)
	 * @return true if the request should be profiled, or false if not
	 */
	protected boolean shouldProfileRequest(HttpServletRequest request) {
		try {
			String relativePath = request.getRequestURI().substring(request.getContextPath().length());
			if(servletContext.getResource(relativePath) == null) {
				// no static resource
				return true;
			}
			// guess if we're hitting a url ending in .jsp then it's not really
			// all that static, so do profiling
			// same for directory access
			return relativePath.endsWith(".jsp") || relativePath.equals("/");

		} catch (MalformedURLException e) {
			// this probably shouldn't happen, but if it does, they're probably not
			// requesting a static resource, which is what we're looking for here
			return true;
		}
	}

	/**
	 * Here so that DI frameworks can inject a profiler provider, rather than
	 * relying on {@link MiniProfiler#start(String)}.
	 *
	 * @param profilerProvider
	 */
	public void setProfilerProvider(ProfilerProvider profilerProvider) {
		this.profilerProvider = profilerProvider;
	}

}
