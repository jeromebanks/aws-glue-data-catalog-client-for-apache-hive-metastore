package com.amazonaws.glue.catalog.converters;


/**
 *  To handle cases where AWS Glue Crawler identifies all S3 URLS as s3://
 *     and all Spark jobs use s3a://
 *
 *   Provide simple conversion between s3a and s3 Urls
 */

public class S3ToS3a {

    public static String S3LocationToS3a(String s3Url) {
        if(s3Url != null) {
            if (s3Url.startsWith("s3://")) {
                return s3Url.replace("s3://", "s3a://");
            } else {
                return s3Url;
            }
        } else {
            return null;
        }
    }

    public static String S3aLocationToS3(String s3aUrl) {
        if(s3aUrl != null) {
            if (s3aUrl.startsWith("s3a://")) {
                return s3aUrl.replace("s3a://", "s3://");
            } else {
                return s3aUrl;
            }
        } else {
            return null;
        }
    }
}
