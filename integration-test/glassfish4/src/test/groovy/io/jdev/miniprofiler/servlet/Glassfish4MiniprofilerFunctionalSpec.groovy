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

package io.jdev.miniprofiler.servlet

import geb.spock.GebReportingSpec
import io.jdev.miniprofiler.test.pages.MiniProfilerGapModule
import io.jdev.miniprofiler.test.pages.MiniProfilerQueryModule
import org.openqa.selenium.Dimension

class Glassfish4MiniprofilerFunctionalSpec extends GebReportingSpec {

	void setup() {
		// ghostdriver way too small otherwise
		driver.manage().window().setSize(new Dimension(1024, 768))
	}

	void "can see miniprofiler"() {
		when:
			to HomePage

		then: 'mini profiler visible with single timing info'
			miniProfiler
			miniProfiler.results.size() == 1
			def result = miniProfiler.results[0]
			result.button.time ==~ ~/\d+\.\d ms/

		and: 'popup not visible'
			!result.popup.displayed

		when: 'click button'
			result.button.click()

		then: 'popup visible'
			result.popup.displayed

		and: ''
			def timings = result.popup.timings
			timings.size() == 1
			timings[0].label == '/'
			timings[0].duration.text() ==~ ~/\d+\.\d/
			!timings[0].durationWithChildren.displayed
			!timings[0].timeFromStart.displayed
	}
}
