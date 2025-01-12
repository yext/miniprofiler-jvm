/*
 * Copyright 2015 the original author or authors.
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

package io.jdev.miniprofiler.ratpack;

import com.google.inject.Provides;
import com.google.inject.Scopes;
import io.jdev.miniprofiler.MiniProfiler;
import io.jdev.miniprofiler.ProfilerProvider;
import ratpack.exec.ExecInterceptor;
import ratpack.guice.ConfigurableModule;

import javax.inject.Singleton;

/**
 * A Guice module to install a Ratpack compatible {@link ProfilerProvider} and an {@link ExecInterceptor}
 * to make all executions profiled.
 *
 * <p>The created {@link ProfilerProvider} is also installed as the default in the {@link MiniProfiler}
 * class for compatibility with code that doesn't use dependency injection or Ratpack contexts.</p>
 *
 * <p>This does <em>not</em> install handlers to support the UI - you'll need to do that separately
 * in your handler chain configuration.</p>
 */
public class MiniProfilerModule extends ConfigurableModule<MiniProfilerModule.Config> {

    /**
     * Installs Ratpack / MiniProfiler support code.
     */
    @Override
    protected void configure() {
        ProfilerProvider provider = new RatpackContextProfilerProvider();
        bind(ProfilerProvider.class).toInstance(provider);
        MiniProfiler.setProfilerProvider(provider);

        // these just here to enable someone to bind a handler etc to the class rather than
        // a new instance if they want to do that
        bind(ExecInterceptor.class).to(MiniProfilerExecInterceptor.class).in(Scopes.SINGLETON);
        bind(MiniProfilerAjaxHeaderHandler.class).in(Scopes.SINGLETON);
        bind(MiniProfilerHandlerChain.class).in(Scopes.SINGLETON);
        bind(StoreMiniProfilerHandler.class).in(Scopes.SINGLETON);
        bind(DiscardMiniProfilerHandler.class).in(Scopes.SINGLETON);
        bind(MiniProfilerResultsHandler.class).in(Scopes.SINGLETON);
        bind(MiniProfilerResourceHandler.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public MiniProfilerExecInterceptor interceptor(ProfilerProvider provider, Config config) {
        return createInterceptor(provider, config);
    }

    protected MiniProfilerExecInterceptor createInterceptor(ProfilerProvider provider, Config config) {
        return new MiniProfilerExecInterceptor(provider, config.defaultProfilerStoreOption);
    }

    public static class Config {
        public ProfilerStoreOption defaultProfilerStoreOption = ProfilerStoreOption.STORE_RESULTS;
    }
}
