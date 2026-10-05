# The two VM identities are what the IAP, Pub/Sub and bucket grants (asks A2 to A4) name.
output "api_service_account" {
  value = google_service_account.api.email
}

output "worker_service_account" {
  value = google_service_account.worker.email
}

output "push_service_account" {
  value = google_service_account.push.email
}

# Give this pair to DevOps for the Route 53 A record (ask A5).
output "lb_ip" {
  value = google_compute_global_address.lb.address
}

output "hostname" {
  value = local.hostname
}

output "image_repo" {
  value = local.image_repo
}

output "secret_ids" {
  value = { for k, s in google_secret_manager_secret.s : k => s.secret_id }
}

output "dlq_topic" {
  value = google_pubsub_topic.dlq.name
}

output "egress_events_subscription" {
  value = local.push_enabled ? google_pubsub_subscription.egress_events[0].name : null
}

# `make rollout-api` / `make rollout-worker` read these.
output "api_template" {
  value = google_compute_instance_template.role["api"].self_link
}

output "worker_template" {
  value = google_compute_instance_template.role["worker"].self_link
}
