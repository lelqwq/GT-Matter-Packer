plugins { id("com.gtnewhorizons.gtnhconvention") }
base { archivesName.set("gtmatterpacker") }
project.ext.set("modVersion", project.property("RELEASE_VERSION"))
tasks.withType<Test>().configureEach { useJUnit() }
