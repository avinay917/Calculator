package com.example.authapp.utils

/**
 * Pagination helper for handling large datasets.
 */
data class PaginationState(
    val pageNumber: Int = 1,
    val pageSize: Int = 20,
    val totalItems: Int = 0,
    val isLoading: Boolean = false,
    val hasMoreData: Boolean = true
) {
    val totalPages: Int = (totalItems + pageSize - 1) / pageSize
    val isLastPage: Boolean = pageNumber >= totalPages
    val offset: Int = (pageNumber - 1) * pageSize

    fun nextPage(): PaginationState {
        return if (!isLastPage) {
            this.copy(pageNumber = pageNumber + 1, isLoading = true)
        } else {
            this
        }
    }

    fun previousPage(): PaginationState {
        return if (pageNumber > 1) {
            this.copy(pageNumber = pageNumber - 1, isLoading = true)
        } else {
            this
        }
    }

    fun reset(): PaginationState {
        return this.copy(pageNumber = 1, isLoading = false)
    }
}

class PaginationHelper<T>(
    val items: List<T> = emptyList(),
    val pageSize: Int = 20
) {
    fun getPage(pageNumber: Int): List<T> {
        val startIndex = (pageNumber - 1) * pageSize
        val endIndex = (startIndex + pageSize).coerceAtMost(items.size)
        return if (startIndex < items.size) {
            items.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    fun getTotalPages(): Int {
        return (items.size + pageSize - 1) / pageSize
    }
}
