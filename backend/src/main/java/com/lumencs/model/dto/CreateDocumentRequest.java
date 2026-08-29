package com.lumencs.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDocumentRequest {
    @NotBlank
    private String title;
    private String source;
    @NotBlank
    private String content;
    /** 默认 true：压缩连续空白 */
    private Boolean collapseWhitespace;
    /** 默认 true：按空行切父段 */
    private Boolean paragraphSplit;
    /** 父段最大字符，默认 500 */
    private Integer parentMax;
    /** 子段（检索）最大字符，默认 200 */
    private Integer childMax;
}
