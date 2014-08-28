package ca.corefacility.bioinformatics.irida.config.analysis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.StandardPasswordEncoder;

import com.github.jmchilton.blend4j.galaxy.HistoriesClient;
import com.github.jmchilton.blend4j.galaxy.ToolsClient;
import com.github.jmchilton.blend4j.galaxy.WorkflowsClient;

import ca.corefacility.bioinformatics.irida.config.conditions.NonWindowsPlatformCondition;
import ca.corefacility.bioinformatics.irida.pipeline.upload.galaxy.GalaxyHistoriesService;
import ca.corefacility.bioinformatics.irida.pipeline.upload.galaxy.GalaxyWorkflowService;
import ca.corefacility.bioinformatics.irida.pipeline.upload.galaxy.integration.LocalGalaxy;
import ca.corefacility.bioinformatics.irida.repositories.joins.sample.SampleSequenceFileJoinRepository;
import ca.corefacility.bioinformatics.irida.service.AnalysisService;
import ca.corefacility.bioinformatics.irida.service.AnalysisSubmissionService;
import ca.corefacility.bioinformatics.irida.service.analysis.execution.galaxy.phylogenomics.impl.AnalysisExecutionServicePhylogenomics;
import ca.corefacility.bioinformatics.irida.service.analysis.workspace.galaxy.phylogenomics.impl.WorkspaceServicePhylogenomics;

/**
 * Test configuration for AnalysisExecutionService classes.
 * @author Aaron Petkau <aaron.petkau@phac-aspc.gc.ca>
 *
 */
@Configuration
@Profile("test")
@Conditional(NonWindowsPlatformCondition.class)
public class AnalysisExecutionServiceTestConfig {

	@Autowired
	private LocalGalaxy localGalaxy;
	
	@Autowired
	private AnalysisSubmissionService analysisSubmissionService;
	
	@Autowired
	private AnalysisService analysisService;
	
	@Autowired
	private SampleSequenceFileJoinRepository sampleSequenceFileJoinRepository;
	
	@Lazy
	@Bean
	public AnalysisExecutionServicePhylogenomics
		analysisExecutionServicePhylogenomics(
				GalaxyHistoriesService galaxyHistoriesService, GalaxyWorkflowService galaxyWorkflowService) {
		
		WorkspaceServicePhylogenomics galaxyWorkflowPreparationServicePhylogenomicsPipeline = 
				new WorkspaceServicePhylogenomics(galaxyHistoriesService, galaxyWorkflowService,
						sampleSequenceFileJoinRepository);
		return new AnalysisExecutionServicePhylogenomics(analysisSubmissionService,
				analysisService, galaxyWorkflowService, galaxyHistoriesService,
				galaxyWorkflowPreparationServicePhylogenomicsPipeline);
	}
	
	@Lazy
	@Bean
	public GalaxyHistoriesService galaxyHistoriesService() {
		HistoriesClient historiesClient = localGalaxy.getGalaxyInstanceWorkflowUser().getHistoriesClient();
		ToolsClient toolsClient = localGalaxy.getGalaxyInstanceWorkflowUser().getToolsClient();
		return new GalaxyHistoriesService(historiesClient, toolsClient);
	}
	
	@Lazy
	@Bean
	public GalaxyWorkflowService galaxyWorkflowService() {
		HistoriesClient historiesClient = localGalaxy.getGalaxyInstanceWorkflowUser().getHistoriesClient();
		WorkflowsClient workflowsClient = localGalaxy.getGalaxyInstanceWorkflowUser().getWorkflowsClient();
		
		return new GalaxyWorkflowService(historiesClient, workflowsClient,
						new StandardPasswordEncoder());
	}
}
