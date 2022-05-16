# Input variable definitions

variable "aws_region" {
  description = "AWS region for all resources."

  type    = string
  default = "eu-west-1"
}

variable "stage" {
  description = "Environment stage"
  type = string
  default = "dev"
}

variable "csv_filename_prefix" {
  description = "Prefix to add to the file to be uploaded to S3."
  type = string
  default = "pets"
}

variable "table_name" {
  description = "Name of the table to audit"
  type = string
}
