package cn.mxleader.quickdoc.service.impl;

import cn.mxleader.quickdoc.dao.ReactiveCategoryRepository;
import cn.mxleader.quickdoc.dao.ReactiveDirectoryRepository;
import cn.mxleader.quickdoc.dao.ReactiveFsDetailRepository;
import cn.mxleader.quickdoc.dao.utils.GridFsAssistant;
import cn.mxleader.quickdoc.entities.FsDetail;
import cn.mxleader.quickdoc.entities.FsDirectory;
import cn.mxleader.quickdoc.security.session.ActiveUser;
import cn.mxleader.quickdoc.service.ReactiveFileService;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static cn.mxleader.quickdoc.common.utils.AuthenticationUtil.READ_PRIVILEGE;
import static cn.mxleader.quickdoc.common.utils.AuthenticationUtil.checkAuthentication;
import static cn.mxleader.quickdoc.common.utils.MessageUtil.fileNotExistMsg;
import static cn.mxleader.quickdoc.dao.utils.QueryTemplate.keyQuery;

@Service
public class ReactiveFileServiceImpl implements ReactiveFileService {

    private static final int BUFFER_SIZE = 8192;

    private final GridFsAssistant gridFsAssistant;
    private final GridFsTemplate gridFsTemplate;
    private final ReactiveCategoryRepository reactiveCategoryRepository;
    private final ReactiveDirectoryRepository reactiveDirectoryRepository;
    private final ReactiveFsDetailRepository reactiveFsDetailRepository;

    ReactiveFileServiceImpl(GridFsAssistant gridFsAssistant,
                            GridFsTemplate gridFsTemplate,
                            ReactiveCategoryRepository reactiveCategoryRepository,
                            ReactiveDirectoryRepository reactiveDirectoryRepository,
                            ReactiveFsDetailRepository reactiveFsDetailRepository) {
        this.gridFsAssistant = gridFsAssistant;
        this.gridFsTemplate = gridFsTemplate;
        this.reactiveCategoryRepository = reactiveCategoryRepository;
        this.reactiveDirectoryRepository = reactiveDirectoryRepository;
        this.reactiveFsDetailRepository = reactiveFsDetailRepository;
    }

    /**
     * 在指定目录内查找相应名字的文件
     *
     * @param filename    输入文件名
     * @param directoryId 所在目录ID
     * @return
     */
    public Mono<FsDetail> getStoredFile(String filename,
                                        ObjectId directoryId) {
        return reactiveFsDetailRepository.findByFilenameAndDirectoryId(filename, directoryId);
    }

    public Mono<FsDetail> getStoredFile(ObjectId fsDetailId) {
        return reactiveFsDetailRepository.findById(fsDetailId);
    }

    /**
     * 枚举目录内的所有文件
     *
     * @param directoryId 所在目录ID
     * @return
     */
    public Flux<FsDetail> getStoredFiles(ObjectId directoryId) {
        return reactiveFsDetailRepository.findAllByDirectoryId(directoryId)
                .map(v -> {
                    v.setCategory(reactiveCategoryRepository.findById(v.getCategoryId()).block().getType());
                    v.setDirectory((reactiveDirectoryRepository.findById(v.getDirectoryId()).block().getPath()));
                    return v;
                });
    }

    /**
     * 存储文件， 如同名文件已存在则更新文件内容
     *
     * @param fsDetail 文件描述信息
     * @param file     文件二进制流
     * @return
     */
    public Mono<FsDetail> storeFile(FsDetail fsDetail,
                                    InputStream file) {
        return getStoredFile(fsDetail.getFilename(), fsDetail.getDirectoryId())
                .defaultIfEmpty(fsDetail)
                .flatMap(
                        entity -> {
                            // 删除库中同名历史文件
                            deleteFile(entity);
                            fsDetail.setId(entity.getId());
                            fsDetail.setStoredId(
                                    gridFsTemplate.store(file,
                                            fsDetail.getFilename(),
                                            fsDetail.getContentType()
                                    )
                            );
                            return reactiveFsDetailRepository.save(fsDetail);
                        }
                );
    }

    /**
     * 删除Mongo库内文件
     *
     * @param fsDetail 文件信息
     * @return
     */
    public Mono<Void> deleteFile(FsDetail fsDetail) {
        return getStoredFile(fsDetail.getFilename(), fsDetail.getDirectoryId())
                .switchIfEmpty(
                        fileNotExistMsg(fsDetail.getDirectoryId(),
                                fsDetail.getFilename())
                )
                .flatMap(entity -> {
                    gridFsTemplate.delete(keyQuery(entity.getStoredId()));
                    return reactiveFsDetailRepository.delete(entity);
                });
    }

    /**
     * 删除Mongo库内文件
     *
     * @param fsDetailId 文件ID信息
     * @return
     */
    public Mono<Void> deleteFile(ObjectId fsDetailId) {
        return reactiveFsDetailRepository.findById(fsDetailId)
                .switchIfEmpty(
                        fileNotExistMsg(fsDetailId)
                )
                .flatMap(entity -> {
                    gridFsTemplate.delete(keyQuery(entity.getStoredId()));
                    return reactiveFsDetailRepository.delete(entity);
                });
    }

    /**
     * 根据输入文件ID获取二进制流
     *
     * @param storedId 文件ID
     * @return
     */
    public GridFSDownloadStream getFileStream(ObjectId storedId) {
        return gridFsAssistant.getResource(storedId);
    }

    /**
     * 创建ZIP文件
     *
     * @param directoryId 文件或文件夹路径
     * @param fos         生成的zip文件存在路径（包括文件名）
     * @param categoryId  待压缩的文件分类ID，为0L则压缩所有分类
     * @param activeUser  当前操作用户信息（用于判断是否有操作权限）
     */
    public void createZip(ObjectId directoryId,
                          OutputStream fos,
                          ObjectId categoryId,
                          ActiveUser activeUser) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(fos);
        Optional<FsDirectory> optionalDirectory = reactiveDirectoryRepository
                .findById(directoryId).blockOptional();
        if (optionalDirectory.isPresent()) {
            FsDirectory directory = optionalDirectory.get();
            compressDirectory(directory, zos, directory.getPath(), categoryId, activeUser);
        } else {
            throw new FileNotFoundException("文件夹ID：" + directoryId);
        }
        zos.close();
    }

    /**
     * 压缩文件夹
     *
     * @param directory  文件夹实体（含路径ID）
     * @param out        ZIP输出流
     * @param basedir    当前目录
     * @param categoryId 待压缩的文件分类ID，为0L则压缩所有分类
     * @param activeUser 当前操作用户信息（用于判断是否有操作权限）
     */
    private void compressDirectory(FsDirectory directory,
                                   ZipOutputStream out,
                                   String basedir,
                                   ObjectId categoryId,
                                   ActiveUser activeUser) {
        // 递归压缩目录
        List<FsDirectory> directories = reactiveDirectoryRepository.findAllByParentId(directory.getId())
                .filter(webDirectory -> checkAuthentication(webDirectory.getOwners(),
                        activeUser, READ_PRIVILEGE))
                .toStream()
                .collect(Collectors.toList());
        if (directories != null && directories.size() > 0) {
            for (FsDirectory subdirectory : directories) {
                compressDirectory(subdirectory, out, basedir + "/" + subdirectory.getPath(),
                        categoryId, activeUser);
            }
        }
        // 压缩目录内的文件
        Flux<FsDetail> fsDetailFlux;
        if (categoryId == null) {
            // 压缩所有分类
            fsDetailFlux = reactiveFsDetailRepository.findAllByDirectoryId(
                    directory.getId())
                    .filter(fsDetail -> checkAuthentication(fsDetail.getOwners(),
                            activeUser, READ_PRIVILEGE));
        } else {
            // 压缩指定分类文件
            fsDetailFlux = reactiveFsDetailRepository.findAllByDirectoryIdAndCategoryId(
                    directory.getId(), categoryId)
                    .filter(fsDetail -> checkAuthentication(fsDetail.getOwners(),
                            activeUser, READ_PRIVILEGE));
        }
        List<FsDetail> fsDetailList = fsDetailFlux.toStream().collect(Collectors.toList());
        if (fsDetailList != null && fsDetailList.size() > 0) {
            for (FsDetail file : fsDetailList) {
                compressFile(getFileStream(file.getStoredId()), out, basedir);
            }
        }
    }

    /**
     * 压缩一个文件
     *
     * @param fsInputStream 输入文件流
     * @param out           输出ZIP流
     * @param basedir       当前文件所在目录
     */
    private void compressFile(GridFSDownloadStream fsInputStream,
                              ZipOutputStream out,
                              String basedir) {
        try {
            BufferedInputStream bis = new BufferedInputStream(fsInputStream);
            ZipEntry entry = new ZipEntry(basedir + "/" + fsInputStream.getGridFSFile().getFilename());
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

}
