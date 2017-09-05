package cn.techfan.quickdoc.repository;

import cn.techfan.quickdoc.common.entities.FsCategory;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoryRepository extends MongoRepository<FsCategory, Long> {

    FsCategory findByType(String type);

}
