CREATE TABLE song (
    id bigint NOT NULL,
    artist character varying(255),
    lyric_id character varying(255),
    name character varying(255)
);

ALTER TABLE song OWNER TO "right-lyrics";

CREATE SEQUENCE song_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE song_id_seq OWNER TO "right-lyrics";

ALTER SEQUENCE song_id_seq OWNED BY song.id;

ALTER TABLE ONLY song ALTER COLUMN id SET DEFAULT nextval('song_id_seq'::regclass);

ALTER TABLE ONLY song ADD CONSTRAINT song_pkey PRIMARY KEY (id);

INSERT INTO song (name, artist, lyric_id) VALUES ('Californication', 'Red Hot Chili Peppers', '5d72534eef68ea00194ac5e8');
INSERT INTO song (name, artist, lyric_id) VALUES ('Everlong', 'Foo Fighters', '5d72535def68ea00194ac5ea');
INSERT INTO song (name, artist, lyric_id) VALUES ('Even Flow', 'Pearl Jam', '5d725355ef68ea00194ac5e9');