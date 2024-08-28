# Elastic Index Config

The files in this directory are used to configure the Elasticsearch indexes in
the catalogue pipeline. It contains files that define the mappings and settings
for the images and works indices at different stages of the pipeline.

## Naming convention

The files in this directory are named according to the following convention:

```
[mappings|analysis].[images|works]_[pipeline_stage].[creation_date].json
```

Where:

- `mappings` or `analysis` indicates whether the file contains mappings or
  analysis settings.
- `images` or `works` indicates whether the file is for the images or works
  index.
- `pipeline_stage` indicates the stage of the pipeline that the file is
  intended for.
- `creation_date` is the date that the file was created in the format
  `YYYY-MM-DD`.

`pipeline_stage` and `creation_date` are used in the pipeline terraform 
[configuration](../pipeline/terraform/) to provision the correct indices.

**Example config block**

```
{
  ...
  index_config = {
    works = {
      identified = "works_identified.2023-05-26"
      merged     = "works_merged.2023-05-26"
      indexed    = "works_indexed.2024-08-20"
    }
    images = {
      indexed        = "images_indexed.2024-08-20"
      works_analysis = "works_indexed.2024-08-20"
    }
  }
  ...
}
```

## Creating a new index configuration

To create a new index configuration, follow these steps:

1. Copy the most recent index configuration file for the index you want to
   configure.
2. Update the file to reflect the changes you want to make.
3. Increment the `creation_date` in the filename according to the naming
   convention.
4. Update the pipeline terraform configuration to use the new index
   configuration file.

> [!Warning]
> Updating / creating new indices outside of the process of creating a new
> pipeline should be done carefully. Changes to the index configuration can
> require a reindex, or recreate indices entirely, which may be undesirable
> if the pipeline is already feeding an index in production use.
