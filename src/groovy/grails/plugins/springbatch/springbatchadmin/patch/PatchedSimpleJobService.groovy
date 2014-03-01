package grails.plugins.springbatch.springbatchadmin.patch

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.batch.admin.service.SearchableJobExecutionDao
import org.springframework.batch.admin.service.SearchableJobInstanceDao
import org.springframework.batch.admin.service.SearchableStepExecutionDao
import org.springframework.batch.admin.service.SimpleJobService
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.configuration.ListableJobLocator
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.NoSuchJobException
import org.springframework.batch.core.launch.NoSuchJobExecutionException
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.repository.dao.ExecutionContextDao
import org.springframework.batch.core.step.StepLocator

/**
 * The only purpose of this class is to create a patched version of the SimpleJobService.
 * There is only one line that has been changed from SimpleJobService, 
 * applying the patch from commit bd0ed8ff9c01e63d5b56f6ed64d38c3ef26e13f8 of the spring batch admin project
 * The entire class should be discarded at the earliest opportunity.
 *
 */
class PatchedSimpleJobService extends SimpleJobService {

	
	private static Log logger = LogFactory.getLog(PatchedSimpleJobService)
	
	private SearchableJobInstanceDao jobInstanceDao;

	private SearchableJobExecutionDao jobExecutionDao;
	
	private ListableJobLocator jobLocator;

	private SearchableStepExecutionDao stepExecutionDao;
	
	public PatchedSimpleJobService(SearchableJobInstanceDao jobInstanceDao, 
			SearchableJobExecutionDao jobExecutionDao,
			SearchableStepExecutionDao stepExecutionDao, 
			JobRepository jobRepository, JobLauncher jobLauncher,
			ListableJobLocator jobLocator, ExecutionContextDao executionContextDao) {

		super(jobInstanceDao, jobExecutionDao, stepExecutionDao, jobRepository,
				jobLauncher, jobLocator, executionContextDao)
		
		this.jobInstanceDao = jobInstanceDao;
		this.jobExecutionDao = jobExecutionDao;
		this.stepExecutionDao = stepExecutionDao;
		this.jobLocator = jobLocator;
	}

	@Override
	public Collection<StepExecution> getStepExecutions(Long jobExecutionId) throws NoSuchJobExecutionException {
		
		JobExecution jobExecution = jobExecutionDao.getJobExecution(jobExecutionId);
		if (jobExecution == null) {
		throw new NoSuchJobExecutionException("No JobExecution with id=" + jobExecutionId);
		}
		
		stepExecutionDao.addStepExecutions(jobExecution);
		
		/**
		 * This patch was applied in  commit bd0ed8ff9c01e63d5b56f6ed64d38c3ef26e13f8 of the spring batch admin project
		 */
		//String jobName = jobExecution.getJobInstance() == null ? null : jobExecution.getJobInstance().getJobName();
		String jobName = jobExecution.getJobInstance() == null ? jobInstanceDao.getJobInstance(jobExecution).getJobName() : jobExecution.getJobInstance().getJobName();
		Collection<String> missingStepNames = new LinkedHashSet<String>();
		
		if (jobName != null) {
		missingStepNames.addAll(stepExecutionDao.findStepNamesForJobExecution(jobName, "*:partition*"));
		logger.debug("Found step executions in repository: " + missingStepNames);
		}
		
		Job job = null;
		try {
		job = jobLocator.getJob(jobName);
		}
		catch (NoSuchJobException e) {
		// expected
		}
		if (job instanceof StepLocator) {
		Collection<String> stepNames = ((StepLocator) job).getStepNames();
		missingStepNames.addAll(stepNames);
		logger.debug("Added step executions from job: " + missingStepNames);
		}
		
		for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
		String stepName = stepExecution.getStepName();
		if (missingStepNames.contains(stepName)) {
		missingStepNames.remove(stepName);
		}
		logger.debug("Removed step executions from job execution: " + missingStepNames);
		}
		
		for (String stepName : missingStepNames) {
		StepExecution stepExecution = jobExecution.createStepExecution(stepName);
		stepExecution.setStatus(BatchStatus.UNKNOWN);
		}
		
		return jobExecution.getStepExecutions();
		
		}
}
