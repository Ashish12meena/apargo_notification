package com.aigreentick.services.notification.service.email.interfaces;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.notification.exceptions.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

public abstract class MongoBaseService<T, ID> implements BaseService<T, ID> {

    /**
     * Subclasses must provide the repository.
     */
    protected abstract MongoRepository<T, ID> getRepository();

    @Override
    @Transactional(readOnly = true)
    public T findById(ID id) {
        return getRepository()
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Entity not found with id " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<T> findOptionalById(ID id) {
        return getRepository().findById(id);
    }

    @Transactional(readOnly = true)
    public List<T> findAll() {
        return getRepository().findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(ID id) {
        return getRepository().existsById(id);
    }

    @Override
    @Transactional
    public T save(T entity) {
        return getRepository().save(entity);
    }

    @Override
    @Transactional
    public List<T> saveAll(List<T> entities) {
        return getRepository().saveAll(entities);
    }

    @Override
    @Transactional
    public void deleteById(ID id) {
        getRepository().deleteById(id);
    }

    @Transactional
    public void delete(T entity) {
        getRepository().delete(entity);
    }
}