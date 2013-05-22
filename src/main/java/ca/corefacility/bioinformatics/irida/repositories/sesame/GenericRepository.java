/*
 * Copyright 2013 Thomas Matthews <thomas.matthews@phac-aspc.gc.ca>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.corefacility.bioinformatics.irida.repositories.sesame;

import ca.corefacility.bioinformatics.irida.exceptions.EntityExistsException;
import ca.corefacility.bioinformatics.irida.exceptions.EntityNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.InvalidPropertyException;
import ca.corefacility.bioinformatics.irida.exceptions.StorageException;
import ca.corefacility.bioinformatics.irida.model.alibaba.IridaThing;
import ca.corefacility.bioinformatics.irida.model.enums.Order;
import ca.corefacility.bioinformatics.irida.model.roles.impl.Audit;
import ca.corefacility.bioinformatics.irida.model.roles.impl.Identifier;
import ca.corefacility.bioinformatics.irida.repositories.CRUDRepository;
import ca.corefacility.bioinformatics.irida.repositories.sesame.dao.SparqlQuery;
import ca.corefacility.bioinformatics.irida.repositories.sesame.dao.TripleStore;
import org.openrdf.annotations.Iri;
import org.openrdf.model.*;
import org.openrdf.query.*;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectQuery;
import org.openrdf.result.Result;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO: Add comments for each of the private and protected data members in this class.
 *
 * @author Thomas Matthews <thomas.matthews@phac-aspc.gc.ca>
 */
public class GenericRepository<IDType extends Identifier, Type extends IridaThing> extends SesameRepository
        implements CRUDRepository<IDType, Type> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(GenericRepository.class);
    protected AuditRepository auditRepo;
    protected RelationshipSesameRepository linksRepo;
    private Class objectType; //The class object type being stored by this repo
    private String prefix;
    private String sType; //String representation of that type

    public GenericRepository() {
    }

    /**
     * TODO: Comment this constructor, I have no idea what many of these arguments are supposed to be (fb)
     *
     * @param store      A {@link TripleStore} to use for storing data in this repository
     * @param objectType The class of objects to store in this repository
     * @param prefix
     * @param sType
     * @param auditRepo
     * @param linksRepo
     */
    public GenericRepository(TripleStore store, Class objectType, String prefix, String sType, AuditRepository auditRepo, RelationshipSesameRepository linksRepo) {
        super(store, sType);

        this.prefix = prefix;
        this.sType = sType;
        this.auditRepo = auditRepo;
        this.linksRepo = linksRepo;

        this.objectType = objectType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Type create(Type object) throws IllegalArgumentException {
        if (object == null) {
            throw new IllegalArgumentException("Object is null");
        }

        Audit audit = (Audit) object.getAuditInformation();

        Identifier objid = generateNewIdentifier(object);

        if (exists(objid)) {
            throw new EntityExistsException("Object " + objid.getUri().toString() + " already exists in the database");
        }

        if (identifierExists(objid.getIdentifier())) {
            throw new EntityExistsException("An object with this identifier already exists in the database");
        }

        object.setIdentifier(objid);

        storeObject(object);
        auditRepo.audit(audit, objid.getUri().toString());

        return object;
    }

    /**
     * TODO: does this method need to be public? Write method-level comments for this method (fb)
     *
     * @param t
     * @return
     */
    public Identifier generateNewIdentifier(Type t) {
        return super.generateNewIdentifier();
    }

    /**
     * TODO: does this method need to be public? Write method-level comments for this method (fb)
     *
     * @param object
     * @return
     */
    public Type storeObject(Type object) {
        ObjectConnection con = store.getRepoConnection();

        Identifier objid = (Identifier) object.getIdentifier();

        try {
            con.begin();
            ValueFactory fac = con.getValueFactory();

            URI uri = fac.createURI(objid.getUri().toString());
            con.addObject(uri, object);

            setIdentifiedBy(con, uri, objid.getIdentifier());

            con.commit();
        } catch (RepositoryException ex) {
            Logger.getLogger(GenericRepository.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            store.closeRepoConnection(con);
        }

        return object;
    }

    /**
     * TODO: does this method need to be public? write method-level comments for this method (fb)
     *
     * @param o
     * @param u
     * @param con
     * @return
     * @throws MalformedQueryException
     * @throws RepositoryException
     * @throws QueryEvaluationException
     */
    public Type buildObjectFromResult(Type o, URI u, ObjectConnection con)
            throws MalformedQueryException, RepositoryException, QueryEvaluationException {
        Type ret = (Type) o.copy();

        String identifiedBy = getIdentifiedBy(con, u);
        Identifier objid = buildIdentifier(ret, u, identifiedBy);
        ret.setIdentifier(objid);
        ret.setAuditInformation(auditRepo.getAudit(u.toString()));

        return ret;
    }

    /**
     * TODO: does this method need to be public? write method-level comments for this method (fb)
     *
     * @param obj
     * @param uri
     * @param identifiedBy
     * @return
     */
    public Identifier buildIdentifier(Type obj, URI uri, String identifiedBy) {
        Identifier objid = new Identifier();
        objid.setUri(java.net.URI.create(uri.toString()));
        objid.setUUID(UUID.fromString(identifiedBy));
        objid.setLabel(obj.getLabel());

        return objid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Type read(Identifier id) throws EntityNotFoundException {
        Type ret = null;

        java.net.URI netURI = buildURIFromIdentifier(id);
        String uri = netURI.toString();

        if (!exists(id)) {
            throw new EntityNotFoundException("No such object with the given URI exists.");
        }

        ObjectConnection con = store.getRepoConnection();

        try {
            String qs = store.getPrefixes()
                    + "SELECT ?s "
                    + "WHERE{ ?s a ?type . \n"
                    + "}";

            ValueFactory fac = con.getValueFactory();
            URI u = fac.createURI(uri);

            Type o = (Type) con.getObject(objectType, u);


            ret = buildObjectFromResult(o, u, con);

        } catch (RepositoryException | QueryEvaluationException | MalformedQueryException ex) {
            logger.error(ex.getMessage());
            throw new StorageException("Failed to read resource");
        } finally {
            store.closeRepoConnection(con);
        }

        return ret;
    }

    /**
     * TODO: This should be exposed in both CRUDRepository and CRUDService.
     *
     * @param idents
     * @return
     */
    public List<Type> readMultiple(List<Identifier> idents) {
        List<Type> projects = new ArrayList<>();
        ObjectConnection con = store.getRepoConnection();

        try {
            //compile a string list of the string URIs
            List<String> uris = new ArrayList<>();
            for (Identifier i : idents) {
                java.net.URI uri = getUriFromIdentifier(i);
                uris.add(uri.toString());
            }
            String[] strArr = new String[uris.size()];
            uris.toArray(strArr);

            Result<Type> result = (Result<Type>) con.getObjects(objectType, strArr);
            while (result.hasNext()) {
                Type o = result.next();

                URI u = con.getValueFactory().createURI(o.toString());

                Type ret = buildObjectFromResult(o, u, con);

                projects.add(ret);
            }
            result.close();
        } catch (RepositoryException | QueryEvaluationException | MalformedQueryException ex) {
            logger.error(ex.getMessage());
            throw new StorageException("Failed to read multiple objects");
        } finally {
            store.closeRepoConnection(con);
        }

        return projects;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean exists(Identifier id) {
        boolean exists = false;
        ObjectConnection con = store.getRepoConnection();

        try {
            java.net.URI netURI = buildURIFromIdentifier(id);
            String uri = netURI.toString();

            String querystring = store.getPrefixes()
                    + "ASK\n"
                    + "{?uri a ?type}";

            BooleanQuery existsQuery = con.prepareBooleanQuery(QueryLanguage.SPARQL, querystring);

            ValueFactory vf = con.getValueFactory();
            URI objecturi = vf.createURI(uri);
            existsQuery.setBinding("uri", objecturi);

            exists = existsQuery.evaluate();


        } catch (RepositoryException | MalformedQueryException | QueryEvaluationException ex) {
            logger.error(ex.getMessage());
            throw new StorageException("Couldn't run exists query");
        } finally {
            store.closeRepoConnection(con);
        }

        return exists;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer count() {
        int count = 0;

        ObjectConnection con = store.getRepoConnection();

        try {
            String qs = store.getPrefixes()
                    + "SELECT (count(?s) as ?c) \n"
                    + "WHERE{ ?s a ?type . \n"
                    + "}\n";

            TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SPARQL, qs);

            URI vtype = con.getValueFactory().createURI(prefix, sType);
            tupleQuery.setBinding("type", vtype);

            TupleQueryResult result = tupleQuery.evaluate();

            BindingSet bindingSet = result.next();
            Value countval = bindingSet.getValue("c");
            count = Integer.parseInt(countval.stringValue());

            result.close();

        } catch (RepositoryException | MalformedQueryException | QueryEvaluationException ex) {
            logger.error(ex.getMessage());
            throw new StorageException("Couldn't run count query");
        } finally {
            store.closeRepoConnection(con);
        }

        return count;
    }

    @Override
    public Type update(IDType id, Map<String, Object> updatedFields) throws InvalidPropertyException {

        java.net.URI netURI = buildURIFromIdentifier(id);
        Audit audit = auditRepo.getAudit(netURI.toString());

        if (exists(id)) {
            for (Entry<String, Object> field : updatedFields.entrySet()) {
                try {
                    Field declaredField = objectType.getDeclaredField(field.getKey());

                    Iri annotation = declaredField.getAnnotation(Iri.class);

                    logger.debug("Updating " + field.getKey() + " -- " + annotation.value());

                    updateField(id, annotation.value(), field.getValue());
                } catch (NoSuchFieldException ex) {
                    logger.error("No field " + field.getKey() + " exists.  Cannot update object.");
                    throw new InvalidPropertyException(
                            "No field named " + field.getKey() + " exists for this object type");
                }
            }
            audit.setUpdated(new Date());
            auditRepo.audit(audit, netURI.toString());
        }

        return read(id);
    }

    private void updateField(IDType id, String predicate, Object value) {
        ObjectConnection con = store.getRepoConnection();
        java.net.URI netURI = buildURIFromIdentifier(id);
        String uri = netURI.toString();

        try {
            con.begin();

            ValueFactory fac = con.getValueFactory();
            URI subURI = fac.createURI(uri);
            URI predURI = fac.createURI(predicate);
            Literal objValue = fac.createLiteral(value);

            RepositoryResult<Statement> curvalues = con.getStatements(subURI, predURI, null);
            while (curvalues.hasNext()) {
                Statement next = curvalues.next();
                logger.debug("current value: " + next.getObject().stringValue());
            }
            con.remove(subURI, predURI, null);

            Statement added = fac.createStatement(subURI, predURI, objValue);
            con.add(added);

            con.commit();
        } catch (RepositoryException ex) {
            logger.error(ex.getMessage());
            throw new StorageException("Failed to update field");
        } finally {
            store.closeRepoConnection(con);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(IDType id) throws EntityNotFoundException {
        if (!exists(id)) {
            throw new EntityNotFoundException("Object does not exist in the database.");
        }

        ObjectConnection con = store.getRepoConnection();

        java.net.URI netURI = buildURIFromIdentifier(id);
        String uri = netURI.toString();

        ValueFactory vf = con.getValueFactory();
        URI objecturi = vf.createURI(uri);

        try {
            con.remove(objecturi, null, null);
            con.close();

        } catch (RepositoryException ex) {
            logger.error(ex.getMessage());
            throw new StorageException("Failed to remove object" + id);
        } finally {
            store.closeRepoConnection(con);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Type> list() {
        return list(0, 0, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Type> list(int page, int size, String sortProperty, Order order) {
        List<Type> users = new ArrayList<>();
        ObjectConnection con = store.getRepoConnection();
        try {
            String qs = store.getPrefixes()
                    + "SELECT ?s "
                    + "WHERE{ ?s a ?type . \n"
                    + "?a irida:forResource ?s \n."
                    + "?a irida:createdDate ?createdDate .\n"
                    + "}";

            qs += SparqlQuery.setOrderBy(sortProperty, order);
            qs += SparqlQuery.setLimitOffset(page, size);


            ObjectQuery query = con.prepareObjectQuery(QueryLanguage.SPARQL, qs);
            query.setType("type", objectType);

            Result<Type> result = (Result<Type>) query.evaluate(objectType);
            //Set<TypeIF> resSet = result.asSet();
            //for(TypeIF o : resSet){
            Set<Type> asSet = result.asSet();
            Iterator<Type> iterator = asSet.iterator();

            //while(result.hasNext()){
            while (iterator.hasNext()) {
                Type o = iterator.next();

                URI u = con.getValueFactory().createURI(o.toString());

                Type ret = buildObjectFromResult(o, u, con);
                users.add(ret);
            }


        } catch (RepositoryException | MalformedQueryException | QueryEvaluationException ex) {
            logger.error(ex.getMessage());
            throw new StorageException("Failed to list objects");
        } finally {
            store.closeRepoConnection(con);
        }
        return users;
    }
}
