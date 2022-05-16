# Output value definitions

output "lambda_bucket_name" {
  description = "Name of the S3 bucket used to store function code."

  value = aws_s3_bucket.bdp-function-code-bucket.id
}

output "function_name" {
  description = "Name of the lambda function."
  value = aws_lambda_function.bdp-function.function_name
}

output "pets_stream" {
  description = "The ARN of the Dynamo Db Stream for pets table."
  value = aws_dynamodb_table.pets_table.stream_arn
}