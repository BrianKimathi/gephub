-- Align DB type with JPA (double precision)
ALTER TABLE kyc_results 
    ALTER COLUMN face_match_score 
    TYPE double precision 
    USING face_match_score::double precision;


