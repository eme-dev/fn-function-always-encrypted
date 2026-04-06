/* 1) Schema */
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'ae')
BEGIN
EXEC(N'CREATE SCHEMA ae AUTHORIZATION dbo;');
END
GO

/* 2) Drop SP primero (depende de la tabla) */
IF OBJECT_ID(N'ae.InsertarClienteAE', N'P') IS NOT NULL
  DROP PROCEDURE [ae].[InsertarClienteAE];
GO

/* 3) Drop table */
IF OBJECT_ID(N'ae.ClienteAE', N'U') IS NOT NULL
DROP TABLE [ae].[ClienteAE];
GO

CREATE TABLE [ae].[ClienteAE](
    [Id] [int] IDENTITY(1,1) NOT NULL,
    [Nombre] [nvarchar](100) NOT NULL,
    [DNI] [nvarchar](12) NOT NULL,
    [DatosJson] [nvarchar](MAX) NULL,
    [Part1] [nvarchar](MAX) COLLATE Latin1_General_BIN2 ENCRYPTED WITH (COLUMN_ENCRYPTION_KEY = [CEK_AKV], ENCRYPTION_TYPE = Randomized, ALGORITHM = 'AEAD_AES_256_CBC_HMAC_SHA_256') NULL,
    [Part2] [nvarchar](MAX) COLLATE Latin1_General_BIN2 ENCRYPTED WITH (COLUMN_ENCRYPTION_KEY = [CEK_AKV], ENCRYPTION_TYPE = Randomized, ALGORITHM = 'AEAD_AES_256_CBC_HMAC_SHA_256') NULL,
    [Part3] [nvarchar](MAX) COLLATE Latin1_General_BIN2 ENCRYPTED WITH (COLUMN_ENCRYPTION_KEY = [CEK_AKV], ENCRYPTION_TYPE = Randomized, ALGORITHM = 'AEAD_AES_256_CBC_HMAC_SHA_256') NULL,
    [Part4] [nvarchar](MAX) COLLATE Latin1_General_BIN2 ENCRYPTED WITH (COLUMN_ENCRYPTION_KEY = [CEK_AKV], ENCRYPTION_TYPE = Randomized, ALGORITHM = 'AEAD_AES_256_CBC_HMAC_SHA_256') NULL,
    [Part5] [nvarchar](MAX) COLLATE Latin1_General_BIN2 ENCRYPTED WITH (COLUMN_ENCRYPTION_KEY = [CEK_AKV], ENCRYPTION_TYPE = Randomized, ALGORITHM = 'AEAD_AES_256_CBC_HMAC_SHA_256') NULL,
    CONSTRAINT [PK_ae_ClienteAE] PRIMARY KEY CLUSTERED ([Id] ASC)
);
GO


CREATE PROCEDURE [ae].[InsertarClienteAE]
    @Nombre     NVARCHAR(200),
    @DNI        NVARCHAR(20),
    @DatosJson  NVARCHAR(MAX),
    @Part1  NVARCHAR(MAX),
    @Part2  NVARCHAR(MAX),
    @Part3  NVARCHAR(MAX),
    @Part4  NVARCHAR(MAX),
    @Part5  NVARCHAR(MAX),
    @OutId INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

INSERT INTO ae.ClienteAE (Nombre, DNI, DatosJson, Part1, Part2, Part3, Part4, Part5)
VALUES (@Nombre, @DNI, @DatosJson,  @Part1, @Part2, @Part3, @Part4, @Part5) ;

-- Devuelve el ID generado mediante SELECT (Compatible con Always Encrypted)
SET @OutId=SCOPE_IDENTITY() ;
END;
