package ca.corefacility.bioinformatics.irida.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.access.prepost.PreAuthorize;

import ca.corefacility.bioinformatics.irida.model.project.Project;
import ca.corefacility.bioinformatics.irida.model.user.User;

/**
 * Specialized repository for {@link Project}.
 * 
 */
public interface ProjectRepository extends IridaJpaRepository<Project, Long> {

	/**
	 * Load up a page of {@link Project}s, excluding the specified
	 * {@link Project}.
	 * 
	 * @param name
	 *            the name of the project to search for
	 * @param exclude
	 *            the project to exclude from results
	 * @param page
	 *            the page request
	 * @return a page of {@link Project}.
	 */
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@Query("from Project p where " + PROJECT_NAME_LIKE + " and " + EXCLUDE_PROJECT)
	public Page<Project> findAllProjectsByNameExcludingProject(final @Param("projectName") String name,
			final @Param("exclude") Project exclude, final Pageable page);

	static final String PROJECT_NAME_LIKE = "(:projectName != '' and p.name like CONCAT(:projectName,'%'))";
	static final String PROJECT_ORGANISM_LIKE = "(:organismName != '' and p.organism like CONCAT(:organismName, '%'))";
	static final String EXCLUDE_PROJECT = "p != :exclude";
	static final String USER_ON_PROJECT = "(:forUser in (select puj.user from ProjectUserJoin puj where puj.project = p))";
	static final String USER_IN_GROUP = "(:forUser in (select ugj.user from UserGroupJoin ugj, UserGroupProjectJoin ugpj where ugj.group = ugpj.userGroup and ugpj.project = p))";
	static final String PROJECT_PERMISSIONS = "(" + USER_ON_PROJECT + " or " + USER_IN_GROUP + ")";

	/**
	 * Load a page of {@link Project}s for a specific {@link User}, excluding a
	 * {@link Project}.
	 * 
	 * @param name
	 *            the name of the project to search for
	 * @param exclude
	 *            the project to exclude from results
	 * @param user
	 *            the user account to load projects for
	 * @param page
	 *            the page request
	 * @return a page of {@link Project}.
	 */
	@Query("from Project p where " + PROJECT_NAME_LIKE + " and " + EXCLUDE_PROJECT + " and " + PROJECT_PERMISSIONS)
	public Page<Project> findProjectsByNameExcludingProjectForUser(final @Param("projectName") String name,
			final @Param("exclude") Project exclude, final @Param("forUser") User user, final Pageable page);

	/**
	 * Load a page of {@link Project}s for a specific {@link User}.
	 * 
	 * @param searchName
	 *            the name of the project to search for
	 * @param searchOrganism
	 *            the name of the organism to search for
	 * @param user
	 *            the user to load projects for
	 * @param page
	 *            the page request
	 * @return a page of {@link Project}.
	 */
	@Query("from Project p where (" + PROJECT_NAME_LIKE + " or " + PROJECT_ORGANISM_LIKE + ") and "
			+ PROJECT_PERMISSIONS)
	public Page<Project> findProjectsForUser(final @Param("projectName") String searchName,
			final @Param("organismName") String searchOrganism, final @Param("forUser") User user, final Pageable page);

	/**
	 * Load all projects in the system that match a name and organism.
	 * 
	 * @param searchName
	 *            the name to search for
	 * @param organismName
	 *            the organism to search for
	 * @param page
	 *            the page request
	 * @return a page of {@link Project}.
	 */
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@Query("from Project p where (" + PROJECT_NAME_LIKE + " or " + PROJECT_ORGANISM_LIKE + ")")
	public Page<Project> findAllProjectsByNameOrOrganism(final @Param("projectName") String searchName,
			final @Param("organismName") String organismName, final Pageable page);
}
