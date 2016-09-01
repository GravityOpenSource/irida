package ca.corefacility.bioinformatics.irida.service.sample;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;

import ca.corefacility.bioinformatics.irida.exceptions.EntityNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.SequenceFileAnalysisException;
import ca.corefacility.bioinformatics.irida.model.joins.Join;
import ca.corefacility.bioinformatics.irida.model.joins.impl.ProjectSampleJoin;
import ca.corefacility.bioinformatics.irida.model.project.Project;
import ca.corefacility.bioinformatics.irida.model.project.ReferenceFile;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.sample.SampleMetadata;
import ca.corefacility.bioinformatics.irida.model.sample.SampleSequencingObjectJoin;
import ca.corefacility.bioinformatics.irida.model.sequenceFile.SequenceFile;
import ca.corefacility.bioinformatics.irida.model.sequenceFile.SequencingObject;
import ca.corefacility.bioinformatics.irida.service.CRUDService;

/**
 * A service class for working with samples.
 * 
 */
public interface SampleService extends CRUDService<Long, Sample> {

	/**
	 * Get a specific instance of a {@link Sample} that belongs to a
	 * {@link Project}. If the {@link Sample} is not associated to the
	 * {@link Project} (i.e., no relationship is shared between the
	 * {@link Sample} and {@link Project}, then an
	 * {@link EntityNotFoundException} will be thrown.
	 * 
	 * @param project
	 *            the {@link Project} to get the {@link Sample} for.
	 * @param identifier
	 *            the identifier of the {@link Sample}
	 * @return the {@link Sample} as requested
	 * @throws EntityNotFoundException
	 *             if no relationship exists between {@link Sample} and
	 *             {@link Project}.
	 */
	public Sample getSampleForProject(Project project, Long identifier) throws EntityNotFoundException;
	
	/**
	 * Find a {@link Sample} assocaited with a {@link SequencingObject}
	 * 
	 * @param seqObject
	 *            the {@link SequencingObject} to get the {@link Sample} for
	 * @return the {@link SampleSequencingObjectJoin} describing the
	 *         relationship
	 */
	public SampleSequencingObjectJoin getSampleForSequencingObject(SequencingObject seqObject);

	/**
	 * Get the list of {@link Sample} that belongs to a specific project.
	 * 
	 * @param project
	 *            the {@link Project} to get samples for.
	 * @return the collection of samples for the {@link Project}.
	 */
	public List<Join<Project, Sample>> getSamplesForProject(Project project);
	
	/**
	 * Get the number of {@link Sample}s for a given {@link Project}. This
	 * method will be faster than getSamplesForProject
	 * 
	 * @param project
	 *            The project to get samples for
	 * @return The number of {@link Sample}s in a given {@link Project}
	 */
	public Long getNumberOfSamplesForProject(Project project);

	/**
	 * Get the {@link Sample}s for a {@link Project} in page form
	 * 
	 * @param project
	 *            The project to read from
	 * @param name
	 *            The sample name to search
	 * @param page
	 *            The page number
	 * @param size
	 *            The size of the page
	 * @param order
	 *            The order of the page
	 * @param sortProperties
	 *            The properties to sort on
	 * @return A {@link Page} of {@link Join}s between {@link Project} and
	 *         {@link Sample}
	 */
	public Page<ProjectSampleJoin> getSamplesForProjectWithName(Project project, String name, int page, int size,
			Direction order, String... sortProperties);

	/**
	 * Get the {@link Sample} for the given ID
	 * 
	 * @param project
	 *            the {@link Project} that the {@link Sample} belongs to.
	 * @param sampleName
	 *            The name for the requested sample
	 * @return A {@link Sample} with the given ID
	 */
	public Sample getSampleBySampleName(Project project, String sampleName);
	
	/**
	 * Remove a {@link SequencingObject} from a given {@link Sample}. This will
	 * delete the {@link SampleSequencingObjectJoin} object
	 * 
	 * @param sample
	 *            {@link Sample} to remove sequences from
	 * @param object
	 *            {@link SequencingObject} to remove
	 */
	public void removeSequencingObjectFromSample(Sample sample, SequencingObject object);

	/**
	 * Merge multiple samples into one. Merging samples copies the
	 * {@link SequenceFile} references from the set of samples into one sample.
	 * The collection of samples in <code>toMerge</code> are marked as deleted.
	 * All samples must be associated with the specified project. The
	 * relationship between each sample in <code>toMerge</code> and the project
	 * p will be deleted.
	 * 
	 * @param p
	 *            the {@link Project} that all samples must belong to.
	 * @param mergeInto
	 *            the {@link Sample} to merge other samples into.
	 * @param toMerge
	 *            the collection of {@link Sample} to merge.
	 * @return the completely merged {@link Sample} (the persisted version of
	 *         <code>mergeInto</code>).
	 */
	public Sample mergeSamples(Project p, Sample mergeInto, Sample... toMerge);

	/**
	 * Given a sample gets the total number of bases in all sequence files in
	 * this sample.
	 * 
	 * @param sample
	 *            The sample to find the total number of bases.
	 * @return The total number of bases in all sequence files in this sample.
	 * @throws SequenceFileAnalysisException
	 *             If there was an error getting FastQC analyses for a sequence
	 *             file.
	 */
	public Long getTotalBasesForSample(Sample sample)
			throws SequenceFileAnalysisException;

	/**
	 * Given the length of a reference file, estimate the total coverage for
	 * this sample.
	 * 
	 * @param sample
	 *            The sample to estimate coverage for.
	 * 
	 * @param referenceFileLength
	 *            The length of the reference file in bases.
	 * @return The estimate coverage of all sequence data in this sample.
	 * @throws SequenceFileAnalysisException
	 *             If there was an error getting FastQC analyses for a sequence
	 *             file.
	 */
	public Double estimateCoverageForSample(Sample sample,
			long referenceFileLength) throws SequenceFileAnalysisException;
	
	/**
	 * Given a {@link ReferenceFile}, estimate the total coverage for
	 * this sample.
	 * 
	 * @param sample
	 *            The sample to estimate coverage for.
	 * 
	 * @param referenceFile
	 *            The {@link ReferenceFile} to estimate coverage for.
	 * @return The estimate coverage of all sequence data in this sample.
	 * @throws SequenceFileAnalysisException
	 *             If there was an error getting FastQC analyses for a sequence
	 *             file.
	 */
	public Double estimateCoverageForSample(Sample sample,
			ReferenceFile referenceFile) throws SequenceFileAnalysisException;
	/**
	 * Get the {@link SampleMetadata} object for a given {@link Sample}
	 *
	 * @param s
	 *            {@link Sample} to read metadata for
	 * @return a {@link SampleMetadata} object
	 */
	public SampleMetadata getMetadataForSample(Sample s);
       
       /**
	 * Save a {@link SampleMetadata} object for a given {@link Sample}. If an
	 * existing {@link SampleMetadata} object exists, this will overwrite it.
	 *
	 * @param sample
	 *            The {@link Sample} to save {@link SampleMetadata} for
	 * @param metadata
	 *            {@link SampleMetadata} object to save.
	 * @return The newly saved {@link SampleMetadata}
	 */
	public SampleMetadata saveSampleMetadaForSample(Sample sample, SampleMetadata metadata);
	
	/**
	 * Delete the {@link SampleMetadata} for a given {@link Sample}
	 * 
	 * @param sample
	 *            The {@link Sample} to delete metadata for
	 */
	public void deleteSampleMetadaForSample(Sample sample);
}
