terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
      version = "~> 4.13.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.1.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

resource "random_pet" "lambda_bucket_name" {
  prefix = "serverless-learning-functions"
  length = 3
}

resource "aws_s3_bucket" "bdp-function-code-bucket" {
  bucket = random_pet.lambda_bucket_name.id
  force_destroy = true
}

resource "aws_s3_object" "bdp-function-code" {
  bucket = aws_s3_bucket.bdp-function-code-bucket.id
  key    = "bdp-function.jar"
  source = "${path.module}/../target/scala-2.13/bdp-function.jar"
  etag = filemd5("${path.module}/../target/scala-2.13/bdp-function.jar")
}

resource "aws_lambda_function" "bdp-function" {
  function_name = "BdpFunction"
  s3_bucket = aws_s3_bucket.bdp-function-code-bucket.id
  s3_key = aws_s3_object.bdp-function-code.key
  runtime = "java11"
  handler = "com.serverless.audit.LambdaInvocation::handleRequest"
  timeout = 300
  memory_size = 1024
  role = aws_iam_role.lambda_exec.arn
  environment {
    variables = {
      CSV_BUCKET = aws_s3_bucket.bdp-function-csv-bucket.id
      CSV_KEY = var.csv_filename_prefix
      TABLE_NAME = var.table_name
    }
  }
}

resource "aws_cloudwatch_log_group" "bdp_function" {
  name = "/aws/lambda/${aws_lambda_function.bdp-function.function_name}"
  retention_in_days = 30
}

resource "aws_iam_role" "lambda_exec" {
  name = "serverless-lambda"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Sid = ""
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_policy" "lambda_policy" {
  name = "lambda_policy"
  description = "Allows lambda to access dynamo db."
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Resource = "*"
      Action = [
        "dynamodb:*"
      ]
    }]
  })
}

resource "aws_iam_policy" "lambda_s3_policy" {
  name = "lambda_s3_policy"
  description = "Allows lambda to access s3."
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Resource = "*"
      Action = [
        "s3:*"
      ]
    }]
  })
}

resource "aws_s3_bucket_policy" "allow_access_from_lambda" {
  bucket = aws_s3_bucket.bdp-function-csv-bucket.id
  policy = data.aws_iam_policy_document.allow_access_from_lambda.json
}

data "aws_iam_policy_document" "allow_access_from_lambda" {
  statement {
    principals {
      identifiers = [aws_iam_role.lambda_exec.arn]
      type        = "AWS"
    }

    actions = [
      "s3:*"
    ]

    resources = [
      aws_s3_bucket.bdp-function-csv-bucket.arn,
      "${aws_s3_bucket.bdp-function-csv-bucket.arn}/*"
    ]
  }
}

resource "aws_iam_role_policy_attachment" "lambda_basic_policy_attachment" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
  role       = aws_iam_role.lambda_exec.name
}

resource "aws_iam_role_policy_attachment" "lambda_s3_policy_attachment" {
  policy_arn = aws_iam_policy.lambda_s3_policy.arn
  role       = aws_iam_role.lambda_exec.name
}

resource "aws_iam_role_policy_attachment" "lambda_policy_attachment" {
  policy_arn = aws_iam_policy.lambda_policy.arn
  role       = aws_iam_role.lambda_exec.name
}

resource "random_pet" "csv_bucket_name" {
  prefix = "serverless-learning-s3-buckets"
  length = 3
}

resource "aws_s3_bucket" "bdp-function-csv-bucket" {
  bucket = random_pet.csv_bucket_name.id
  force_destroy = true
}

resource "aws_cloudwatch_event_rule" "bdp_function_schedule" {
  name = "bdp-function-schedule"
  description = "Event to invoke the bdp function."
  schedule_expression = "rate(3 minutes)"
}

resource "aws_cloudwatch_event_target" "bdp_function_event_target" {
  arn  = aws_lambda_function.bdp-function.arn
  rule = aws_cloudwatch_event_rule.bdp_function_schedule.name
}

resource "aws_lambda_permission" "allow_cloudwatch" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.bdp-function.function_name
  principal     = "events.amazonaws.com"
  statement_id  = "AllowExecutionFromCloudWatch"
  source_arn = aws_cloudwatch_event_rule.bdp_function_schedule.arn
}

resource "aws_lambda_alias" "bdp_function_alias" {
  name             = "bdpfunction"
  description      = "Bdp Function Alias"
  function_name    = aws_lambda_function.bdp-function.function_name
  function_version = "$LATEST"
}

resource "aws_dynamodb_table" "shard_iterator_checkpoint_table" {
  name = "IteratorCheckpointTable"
  billing_mode = "PROVISIONED"
  read_capacity = 1
  write_capacity = 1
  hash_key = "iterator"

  attribute {
    name = "iterator"
    type = "S"
  }

  tags = {
    Name        = "dynamodb-iterator-checkpoint-table"
    Environment = var.stage
  }
}