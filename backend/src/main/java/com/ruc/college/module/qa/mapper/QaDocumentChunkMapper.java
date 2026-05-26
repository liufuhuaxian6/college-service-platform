package com.ruc.college.module.qa.mapper;

import com.ruc.college.module.qa.entity.QaDocumentChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QaDocumentChunkMapper {

    @Delete("DELETE FROM qa_document_chunk WHERE document_id = #{documentId}")
    int deleteByDocumentId(@Param("documentId") Long documentId);

    @Insert("""
            INSERT INTO qa_document_chunk
              (document_id, title, category, chunk_index, content, keywords, embedding)
            VALUES
              (#{chunk.documentId}, #{chunk.title}, #{chunk.category}, #{chunk.chunkIndex},
               #{chunk.content}, #{chunk.keywords}, #{embedding}::vector)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "chunk.id")
    int insertChunk(@Param("chunk") QaDocumentChunk chunk, @Param("embedding") String embedding);

    @Select("""
            SELECT id, document_id AS documentId, title, category, chunk_index AS chunkIndex,
                   content, keywords, created_at AS createdAt,
                   (1 - (embedding <=> #{embedding}::vector)) AS score
            FROM qa_document_chunk
            WHERE (#{category} = '' OR category = #{category})
            ORDER BY embedding <=> #{embedding}::vector
            LIMIT #{limit}
            """)
    List<QaDocumentChunk> searchSimilar(@Param("embedding") String embedding,
                                        @Param("category") String category,
                                        @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM qa_document_chunk WHERE document_id = #{documentId}")
    int countByDocumentId(@Param("documentId") Long documentId);
}
