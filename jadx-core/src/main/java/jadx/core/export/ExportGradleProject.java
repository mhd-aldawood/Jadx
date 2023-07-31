package jadx.core.export;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import jadx.api.ResourceFile;
import jadx.core.dex.nodes.RootNode;
import jadx.core.utils.android.AndroidManifestParser;
import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.core.xmlgen.ResContainer;
import jadx.core.xmlgen.XmlSecurity;

public class ExportGradleProject {
	private static final Pattern ILLEGAL_GRADLE_CHARS = Pattern.compile("[/\\\\:>\"?*|]");

	private final RootNode root;
	private final File projectDir;
	private final File appDir;
	private final ApplicationParams applicationParams;

	public ExportGradleProject(RootNode root, File projectDir, ResourceFile androidManifest, ResContainer appStrings) {
		this.root = root;
		this.projectDir = projectDir;
		this.appDir = new File(projectDir, "app");
		this.applicationParams = getApplicationParams(androidManifest, appStrings);
	}

	public void generateGradleFiles() {
		try {
			saveProjectBuildGradle();
			saveApplicationBuildGradle();
			saveSettingsGradle();
		} catch (Exception e) {
			throw new JadxRuntimeException("Gradle export failed", e);
		}
	}

	private void saveProjectBuildGradle() throws IOException {
		TemplateFile tmpl = TemplateFile.fromResources("/export/build.gradle.tmpl");
		tmpl.save(new File(projectDir, "build.gradle"));
	}

	private void saveSettingsGradle() throws IOException {
		TemplateFile tmpl = TemplateFile.fromResources("/export/settings.gradle.tmpl");

		tmpl.add("applicationName", ILLEGAL_GRADLE_CHARS.matcher(applicationParams.getApplicationName()).replaceAll(""));
		tmpl.save(new File(projectDir, "settings.gradle"));
	}

	private void saveApplicationBuildGradle() throws IOException {
		TemplateFile tmpl = TemplateFile.fromResources("/export/app.build.gradle.tmpl");
		String appPackage = root.getAppPackage();

		if (appPackage == null) {
			appPackage = "UNKNOWN";
		}

		Integer minSdkVersion = applicationParams.getMinSdkVersion();

		tmpl.add("applicationId", appPackage);
		tmpl.add("minSdkVersion", minSdkVersion);
		tmpl.add("targetSdkVersion", applicationParams.getTargetSdkVersion());
		tmpl.add("versionCode", applicationParams.getVersionCode());
		tmpl.add("versionName", applicationParams.getVersionName());

		List<String> additionalOptions = new ArrayList<>();
		GradleInfoStorage gradleInfo = root.getGradleInfoStorage();
		if (gradleInfo.isVectorPathData() && minSdkVersion < 21 || gradleInfo.isVectorFillType() && minSdkVersion < 24) {
			additionalOptions.add("vectorDrawables.useSupportLibrary = true");
		}
		if (gradleInfo.isUseApacheHttpLegacy()) {
			additionalOptions.add("useLibrary 'org.apache.http.legacy'");
		}
		genAdditionalAndroidPluginOptions(tmpl, additionalOptions);

		tmpl.save(new File(appDir, "build.gradle"));
	}

	private void genAdditionalAndroidPluginOptions(TemplateFile tmpl, List<String> additionalOptions) {
		StringBuilder sb = new StringBuilder();
		for (String additionalOption : additionalOptions) {
			sb.append("        ").append(additionalOption).append('\n');
		}
		tmpl.add("additionalOptions", sb.toString());
	}

	private ApplicationParams getApplicationParams(ResourceFile androidManifest, ResContainer appStrings) {
		AndroidManifestParser parser = new AndroidManifestParser(androidManifest, appStrings,
				AndroidManifestParser.APPLICATION_LABEL
						| AndroidManifestParser.MIN_SDK_VERSION
						| AndroidManifestParser.TARGET_SDK_VERSION
						| AndroidManifestParser.VERSION_CODE
						| AndroidManifestParser.VERSION_NAME);
		return parser.getParseResults();
	}

	private Document parseXml(String xmlContent) {
		try {
			DocumentBuilder builder = XmlSecurity.getSecureDbf().newDocumentBuilder();
			Document document = builder.parse(new InputSource(new StringReader(xmlContent)));

			document.getDocumentElement().normalize();

			return document;
		} catch (Exception e) {
			throw new JadxRuntimeException("Can not parse xml content", e);
		}
	}

	private Document parseAppStrings(ResContainer appStrings) {
		String content = appStrings.getText().getCodeStr();

		return parseXml(content);
	}

	private Document parseAndroidManifest(ResourceFile androidManifest) {
		String content = androidManifest.loadContent().getText().getCodeStr();

		return parseXml(content);
	}
}
