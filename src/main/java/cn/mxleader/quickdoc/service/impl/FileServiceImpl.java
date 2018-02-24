package cn.mxleader.quickdoc.service.impl;

import cn.mxleader.quickdoc.common.utils.FileUtils;
import cn.mxleader.quickdoc.dao.utils.GridFsAssistant;
import cn.mxleader.quickdoc.entities.FileMetadata;
import cn.mxleader.quickdoc.entities.QuickDocFolder;
import cn.mxleader.quickdoc.security.entities.ActiveUser;
import cn.mxleader.quickdoc.service.FileService;
import cn.mxleader.quickdoc.web.domain.WebFile;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsCriteria;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static cn.mxleader.quickdoc.web.config.AuthenticationToolkit.READ_PRIVILEGE;
import static cn.mxleader.quickdoc.web.config.AuthenticationToolkit.checkAuthentication;

@Service
public class FileServiceImpl implements FileService {

    private static final int BUFFER_SIZE = 8192;

    private final GridFsAssistant gridFsAssistant;
    private final MongoTemplate mongoTemplate;
    private final MongoConverter converter;

    FileServiceImpl(GridFsAssistant gridFsAssistant,
                    MongoTemplate mongoTemplate,
                    MongoConverter converter) {
        this.gridFsAssistant = gridFsAssistant;
        this.mongoTemplate = mongoTemplate;
        this.converter = converter;
    }

    public WebFile getStoredFile(ObjectId fileId) {
        return switchWebFile(gridFsAssistant.findOne(new Query(Criteria.where("_id").is(fileId))));
    }

    /**
     * 在指定目录内查找相应名字的文件
     *
     * @param filename 输入文件名
     * @param folderId 所在目录ID
     * @return
     */
    public WebFile getStoredFile(String filename, ObjectId folderId) {
        Query query = Query.query(GridFsCriteria.whereFilename().is(filename));
        query.addCriteria(GridFsCriteria.whereMetaData("folderId").is(folderId));
        return switchWebFile(gridFsAssistant.findOne(query));
    }

    /**
     * 枚举目录内的所有文件
     *
     * @param folderId 所在目录ID
     * @return
     */
    public Stream<WebFile> getWebFiles(ObjectId folderId) {
        return StreamSupport.stream(switchWebFiles(getStoredFiles(folderId)).spliterator(), false);
    }

    private GridFSFindIterable getStoredFiles(ObjectId folderId) {
        Query query = Query.query(GridFsCriteria.whereMetaData("folderId").is(folderId));
        return gridFsAssistant.find(query);
    }

    /**
     * 根据文件名进行模糊查询
     *
     * @param filename
     * @return
     */
    public Stream<WebFile> searchFilesContaining(String filename) {
        Pattern pattern = Pattern.compile("^.*" + filename + ".*$", Pattern.CASE_INSENSITIVE);
        Query query = Query.query(GridFsCriteria.whereFilename().is(pattern));
        return StreamSupport.stream(switchWebFiles(gridFsAssistant.find(query)).spliterator(), false);
    }

    /**
     * 存储文件， 如同名文件已存在则更新文件内容
     *
     * @param file        文件内容输入流
     * @param filename    文件名
     * @param contentType 文件类型
     * @return
     */
    public ObjectId store(InputStream file,
                          String filename,
                          String contentType) {
        return gridFsAssistant.store(file, filename, contentType);
    }

    public ObjectId store(InputStream file,
                          String filename,
                          FileMetadata metadata) {
        return gridFsAssistant.store(file, filename, metadata);
    }

    public void rename(ObjectId fileId, String newFilename) {
        gridFsAssistant.rename(fileId, newFilename);
    }

    public GridFSFile saveMetadata(ObjectId fileId, FileMetadata fileMetadata) {
        return gridFsAssistant.updateMetadata(fileId, fileMetadata);
    }

    /**
     * 删除Mongo库内文件
     *
     * @param fileId 文件ID
     * @return
     */
    public void delete(ObjectId fileId) {
        gridFsAssistant.delete(new Query(Criteria.where("_id").is(fileId)));
    }

    /**
     * 根据输入文件ID获取二进制流
     *
     * @param fileId 文件ID
     * @return
     */
    @Override
    public GridFsResource getResource(ObjectId fileId) {
        return gridFsAssistant.getResource(fileId);
    }

    @Override
    public GridFSDownloadStream getFSDownloadStream(ObjectId fileId) {
        return gridFsAssistant.getFSDownloadStream(fileId);
    }

    /**
     * 创建ZIP文件
     *
     * @param folderId   文件或文件夹路径
     * @param fos        生成的zip文件存在路径（包括文件名）
     * @param activeUser 当前操作用户信息（用于判断是否有操作权限）
     */
    public void createZip(ObjectId folderId,
                          OutputStream fos,
                          ActiveUser activeUser) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(fos);
        QuickDocFolder folder = mongoTemplate.findById(folderId, QuickDocFolder.class);
        if (folder != null) {
            compressFolder(folder, zos, folder.getPath(), activeUser);
        } else {
            throw new FileNotFoundException("文件夹ID：" + folderId);
        }
        zos.close();
    }

    /**
     * 压缩文件夹
     *
     * @param folder     文件夹实体（含路径ID）
     * @param out        ZIP输出流
     * @param basedir    当前目录
     * @param activeUser 当前操作用户信息（用于判断是否有操作权限）
     */
    private void compressFolder(QuickDocFolder folder,
                                ZipOutputStream out,
                                String basedir,
                                ActiveUser activeUser) {
        // 递归压缩目录
        List<QuickDocFolder> folders = mongoTemplate.find(
                Query.query(Criteria.where("parentId").is(folder.getId())),
                QuickDocFolder.class).stream()
                .filter(quickDocFolder -> checkAuthentication(quickDocFolder.getOpenAccess(),
                        quickDocFolder.getAuthorizations(),
                        activeUser, READ_PRIVILEGE))
                .collect(Collectors.toList());
        if (folders != null && folders.size() > 0) {
            for (QuickDocFolder subFolder : folders) {
                compressFolder(subFolder, out, basedir + "/" + subFolder.getPath(),
                        activeUser);
            }
        }
        // 压缩目录内的文件
        switchWebFiles(getStoredFiles(folder.getId()))
                .forEach(file -> {
                    if (checkAuthentication(file.getOpenAccess(),
                            file.getAuthorizations(),
                            activeUser, READ_PRIVILEGE)) {
                        compressFile(getResource(file.getId()), out, basedir);
                    }
                });

    }

    /**
     * 压缩一个文件
     *
     * @param gridFsResource 输入文件流
     * @param out            输出ZIP流
     * @param basedir        当前文件所在目录
     */
    private void compressFile(GridFsResource gridFsResource,
                              ZipOutputStream out,
                              String basedir) {
        try {
            BufferedInputStream bis = new BufferedInputStream(gridFsResource.getInputStream());
            ZipEntry entry = new ZipEntry(basedir + "/" + gridFsResource.getFilename());
            out.putNextEntry(entry);
            int count;
            byte data[] = new byte[BUFFER_SIZE];
            while ((count = bis.read(data, 0, BUFFER_SIZE)) != -1) {
                out.write(data, 0, count);
            }
            bis.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * MongoDB存储格式转换为WEB显示格式的文件
     *
     * @param gridFSFile GridFSFile格式文件
     * @return Web格式文件
     */
    private WebFile switchWebFile(GridFSFile gridFSFile) {
        if (gridFSFile == null) return null;
        FileMetadata metadata = converter.read(FileMetadata.class, gridFSFile.getMetadata());
        return new WebFile(gridFSFile.getObjectId(),
                gridFSFile.getFilename(),
                gridFSFile.getLength(),
                gridFSFile.getUploadDate(),
                metadata.get_contentType(),
                metadata.getFolderId(),
                metadata.getOpenAccess(),
                metadata.getAuthorizations(),
                metadata.getLabels(),
                FileUtils.getLinkPrefix(metadata.get_contentType()),
                FileUtils.getIconClass(metadata.get_contentType())
        );
    }

    /**
     * 批量转换文件格式
     *
     * @param gridFSFindIterable
     * @return
     */
    private Iterable<WebFile> switchWebFiles(GridFSFindIterable gridFSFindIterable) {
        return gridFSFindIterable.map(gridFSFile -> switchWebFile(gridFSFile));
    }
}