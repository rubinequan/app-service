package cn.wildfirechat.app.jpa;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;

@Repository
public interface ReportRepository extends CrudRepository<Report,Long> {

    @Transactional
    @Modifying
    @Query(value="update report set status=?1 where tid=?2",nativeQuery=true)
    Integer updateById(Integer status,String id);

}
